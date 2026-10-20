import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
public final class DisassembleCompat {
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = new DexBackedDexFile(new Opcodes(35, -1), Files.readAllBytes(Paths.get(args[0])), 0);
        BaksmaliOptions options = new BaksmaliOptions();
        options.localsDirective = true;
        Baksmali.disassembleDexFile(dex, new File(args[1]), 4, options);
    }
}
