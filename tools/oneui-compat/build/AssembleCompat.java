import java.io.File;
import java.nio.file.*;
import brut.androlib.src.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
public final class AssembleCompat {
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);
        SmaliBuilder assembler=new SmaliBuilder(root.toFile(), 35);
        DexBuilder dex=new DexBuilder(new Opcodes(35, -1));
        try (java.util.stream.Stream<Path> files=Files.walk(root)) {
            for (Path file:(Iterable<Path>)files.filter(p -> p.toString().endsWith(".smali"))::iterator)
                assembler.buildFile(root.relativize(file).toString().replace('\\','/'), dex);
        }
        FileDataStore store=new FileDataStore(new File(args[1]));
        dex.writeTo(store);
        store.raf.close();
    }
}
