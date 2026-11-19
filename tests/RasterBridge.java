package hk.luce.printerlab;
import java.nio.file.*;
import java.util.Scanner;
public final class RasterBridge {
 public static void main(String[] args)throws Exception{
  boolean[][] img;
  try(Scanner s=new Scanner(Path.of(args[0]))){
   if(!s.next().equals("P1"))throw new IllegalArgumentException("PBM P1 required");
   int w=s.nextInt(),h=s.nextInt();if(w>384||h>640)throw new IllegalArgumentException("Bounds");
   img=new boolean[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++)img[y][x]=s.nextInt()==1;
  }
  Files.write(Path.of(args[1]),Protocol.raster(img));Files.write(Path.of(args[2]),Protocol.column24(img));
 }
}
