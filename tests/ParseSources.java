import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
/** Syntax parse only; deliberately NOT presented as an Android compilation. */
public class ParseSources {
 public static void main(String[] args)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)){
   List<java.io.File> files=new ArrayList<>();
   try(var paths=Files.walk(Path.of(args[0]))){paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile()));}
   JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--release","17"),null,manager.getJavaFileObjectsFromFiles(files));
   task.parse();int errors=0;for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){errors++;System.err.println(d);}
   if(errors!=0)throw new AssertionError("Java parse errors");
   System.out.println("SYNTAX_ONLY: "+files.size()+" Java files parsed; Android API linkage NOT checked");
  }
 }
}
