#!/usr/bin/env python3
"""SQLite query contract checks from actual Journal.java; NOT Android SQLite execution."""
from pathlib import Path
import re
import sqlite3

root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/hk/luce/printerlab/Journal.java').read_text()
checks=[]
def check(condition,label):
    if not condition:raise AssertionError(label)
    checks.append(label);print('PASS SQL',len(checks),label)

def main():
    db=sqlite3.connect(':memory:')
    for sql in re.findall(r'db\.execSQL\("(CREATE TABLE [^"\n]+)"\)',source):db.execute(sql)
    db.execute("INSERT INTO attempts(id,at_ms,method,device_tag,packet_bytes,packet_sha256,settings,state,observation) VALUES ('old',9000,'A','one',10,'hash','settings','sending','not_checked')")
    db.execute("INSERT INTO attempts(id,at_ms,method,device_tag,packet_bytes,packet_sha256,settings,state,observation) VALUES ('new',1,'B','one',10,'hash','settings','sent_unconfirmed','printed_ok')")
    db.execute("INSERT INTO attempts(id,at_ms,method,device_tag,packet_bytes,packet_sha256,settings,state,observation) VALUES ('other',99999,'C','two',10,'hash','settings','sent_unconfirmed','qr_decoded_match')")
    recover=re.search(r'execSQL\("(UPDATE attempts SET state=.+?)"\)',source).group(1)
    db.execute(recover)
    check(db.execute("SELECT state FROM attempts WHERE id='old'").fetchone()[0]=='interrupted_unknown','original recovery marks interrupted without deleting')
    query=re.search(r'rawQuery\("(SELECT id,method,device_tag,state,observation,rowid[^"\n]+)"',source).group(1)
    rows=db.execute(query,('one',)).fetchall()
    check([r[0] for r in rows]==['new','old'],'insertion order survives backwards wall-clock jump')
    check(all(r[2]=='one' for r in rows),'other printer rows excluded')
    check(not db.execute(query,("one' OR 1=1 --",)).fetchall(),'device tag parameter cannot alter query')
    db.execute("UPDATE attempts SET observation='qr_decoded_mismatch' WHERE id='new'")
    check(db.execute(query,('one',)).fetchone()[4]=='qr_decoded_mismatch','new observation persists without schema migration')
    for i in range(70):db.execute("INSERT INTO attempts(id,at_ms,method,device_tag,packet_bytes,packet_sha256,settings,state,observation) VALUES (?,?, 'A','one',10,'hash','settings','sent_unconfirmed','printed_ok')",(f'fake-{i}',i))
    history=re.search(r'rawQuery\("(SELECT id,method,state,observation FROM attempts[^"\n]+)"',source).group(1)
    check('old' in [r[0] for r in db.execute(history)],'history can still record retained unresolved rows older than 60')
    check(len(list(db.execute(query,('one',))))==72,'same-device retained history remains intact')
    print('TOTAL',len(checks),'SQLite query-contract checks; not Android runtime')
if __name__=='__main__':main()
