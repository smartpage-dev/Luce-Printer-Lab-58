#!/usr/bin/env python3
"""Compile/run the real button helper + its two checked-throwing call sites.

The test uses deliberately minimal view doubles, not Android implementations.
This catches Java target-typing errors; it is NOT an Android SDK build, lint,
UI lifecycle test, Bluetooth test, or proof of physical printing.
A negative control replaces only CheckedAction with Runnable and must fail.
"""
from pathlib import Path
import argparse
import json
import re
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]

def block_at(source: str, marker: str) -> str:
    start=source.index(marker)
    brace=source.index('{',start)
    depth=0
    for i in range(brace,len(source)):
        if source[i]=='{': depth+=1
        elif source[i]=='}':
            depth-=1
            if depth==0: return source[start:i+1]
    raise ValueError('Unbalanced source block: '+marker)

def harness(source: str) -> str:
    helper=block_at(source,'private Button button(')
    declaration=''
    if 'private interface CheckedAction' in source:
        declaration='@FunctionalInterface\n'+block_at(source,'private interface CheckedAction')
    calls=[]
    for label in ['Preview selected test (does not print)','Send ONE selected test...']:
        found=[l.strip() for l in source.splitlines() if 'button(print,' in l and '"'+label+'"' in l]
        if len(found)!=1: raise AssertionError('Expected one actual call site: '+label)
        calls.append(found[0])
    return '''import java.util.*;
public final class UiCallbackHarness {
    static class View { interface OnClickListener { void onClick(View v); } }
    static final class Button extends View {
        String title; boolean allCaps=true; OnClickListener listener;
        Button(Object context) {}
        void setText(String text) {title=text;}
        void setAllCaps(boolean value) {allCaps=value;}
        void setOnClickListener(OnClickListener value) {listener=value;}
        void click() {listener.onClick(this);}
    }
    static final class LinearLayout {
        final List<Button> children=new ArrayList<>();
        static final class LayoutParams { LayoutParams(int w,int h) {} }
        void addView(Button view,LayoutParams p) {children.add(view);}
    }
    final List<View> lockViews=new ArrayList<>();
    final List<Exception> errors=new ArrayList<>();
    final LinearLayout print=new LinearLayout();
    int packets,previews,confirmations;
    Exception packetFailure,confirmFailure;
    void showError(Exception e) {errors.add(e);}
    Object makePacket() throws Exception {packets++; if(packetFailure!=null)throw packetFailure; return new Object();}
    void showPreview(Object packet) {previews++;}
    void confirmPrint() throws Exception {confirmations++;if(confirmFailure!=null)throw confirmFailure;}
''' + declaration+'\n'+helper+'''
    void registerRealCallSites() {
'''+'\n'.join(calls)+'''
    }
    static int checks;
    static void check(boolean condition,String label) {
        if(!condition)throw new AssertionError(label);
        checks++;System.out.println("PASS UI_CALLBACK "+checks+" "+label);
    }
    public static void main(String[] args) {
        UiCallbackHarness h=new UiCallbackHarness();h.registerRealCallSites();
        check(h.print.children.size()==2,"both actual call sites registered");
        check(h.packets==0&&h.previews==0&&h.confirmations==0,"registration does not invoke or print");
        check(h.lockViews.size()==2,"both controls remain included in busy locking");
        check(!h.print.children.get(0).allCaps&&!h.print.children.get(1).allCaps,"original button presentation retained");
        h.print.children.get(0).click();
        check(h.packets==1&&h.previews==1&&h.confirmations==0,"checked-throwing preview lambda executes once");
        h.print.children.get(1).click();
        check(h.confirmations==1&&h.errors.isEmpty(),"checked-throwing method reference executes once");
        Exception checked=new Exception("preview fixture");h.packetFailure=checked;h.print.children.get(0).click();
        check(h.errors.size()==1&&h.errors.get(0)==checked,"preview checked exception reaches existing error handler");
        check(h.packets==2&&h.previews==1,"failed packet never reaches preview; no auto-retry");
        Exception send=new java.io.IOException("confirmation fixture");h.confirmFailure=send;h.print.children.get(1).click();
        check(h.errors.size()==2&&h.errors.get(1)==send,"confirmation checked exception reaches existing error handler");
        check(h.confirmations==2,"failed confirmation is not replayed");
        RuntimeException runtime=new IllegalArgumentException("invalid fixture");h.confirmFailure=runtime;h.print.children.get(1).click();
        check(h.errors.size()==3&&h.errors.get(2)==runtime,"runtime validation error still handled");
        int old=h.lockViews.size();int[] ordinary={0};
        Button b=h.button(h.print,"ordinary",()->ordinary[0]++,false);
        check(h.lockViews.size()==old&&ordinary[0]==0,"ordinary callbacks do not execute during registration");
        b.click();check(ordinary[0]==1,"ordinary callback behavior retained");
        Error fatal=new AssertionError("fatal fixture");Button bad=h.button(h.print,"fatal",()->{throw fatal;},false);
        boolean propagated=false;try {bad.click();}catch(Error e){propagated=e==fatal;}
        check(propagated&&h.errors.size()==3,"fatal JVM errors not disguised as successful actions");
        System.out.println("UI_CALLBACK_ASSERTIONS="+checks);
    }
}
'''

def compile_harness(text: str, folder: Path):
    folder.mkdir()
    file=folder/'UiCallbackHarness.java';file.write_text(text,encoding='utf-8')
    return subprocess.run(['javac','-XDrawDiagnostics','--release','17','-d',str(folder),str(file)],
                          text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root',type=Path,default=ROOT)
    parser.add_argument('--expect-original-failure',action='store_true')
    args=parser.parse_args()
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('A JDK is required; refusing to skip callback compilation.')
    main_file=args.source_root/'app/src/main/java/hk/luce/printerlab/MainActivity.java'
    source=main_file.read_text();text=harness(source)
    with tempfile.TemporaryDirectory(prefix='printerlab-callback-') as temp:
        work=Path(temp)
        first=compile_harness(text,work/'actual')
        if args.expect_original_failure:
            print(first.stdout)
            if first.returncode==0 or not any(m in first.stdout for m in ['incompatible.thrown.types','unreported.exception']):
                raise AssertionError('Original did not reproduce the checked-exception type failure')
            print('EXPECTED_ORIGINAL_FAILURE: actual helper and call sites reject checked exceptions')
            return
        if first.returncode:
            raise AssertionError('Actual callback contract failed Java type checking:\n'+first.stdout)
        run=subprocess.run(['java','-cp',str(work/'actual'),'UiCallbackHarness'],text=True,
                           stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30)
        print(run.stdout,end='')
        if run.returncode: raise AssertionError('Callback harness failed')
        mutant=text.replace('String name,CheckedAction action,boolean lock','String name,Runnable action,boolean lock')
        if mutant==text: raise AssertionError('Missing expected checked callback contract')
        negative=compile_harness(mutant,work/'negative')
        if negative.returncode==0 or not any(m in negative.stdout for m in ['incompatible.thrown.types','unreported.exception']):
            raise AssertionError('Negative control failed to detect original error:\n'+negative.stdout)
        print('PASS UI_CALLBACK_NEGATIVE_CONTROL: reverting to Runnable is rejected by javac')
        print(negative.stdout)
        print(json.dumps({'scope':'actual extracted callback + call-site Java semantics with view doubles',
                          'behavior_assertions':14,'negative_controls':1,
                          'full_android_build':False,'hardware_test':False}))

if __name__=='__main__':main()
