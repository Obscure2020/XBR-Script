import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

class Main {
    private static Path input_parent;
    private static Path output_parent;
    private static boolean input_is_zipped = false;

    private static void abort(String reason){
        System.out.println("ABORT.");
        System.out.println();
        System.out.println(reason);
        System.exit(1);
    }

    private static void detectAndValidateZippedInput(){
        if(Files.isDirectory(input_parent, LinkOption.NOFOLLOW_LINKS)){
            input_is_zipped = false;
            return;
        }
        if(!Files.isRegularFile(input_parent, LinkOption.NOFOLLOW_LINKS)){
            abort("Input path exists, but is neither a directory nor a file.");
        }
        try(ZipFile zip = new ZipFile(input_parent.toFile())){
            input_is_zipped = true;
        } catch (Exception e) {
            abort("Input path appears to be a file, but I cannot confirm it's a ZIP or JAR.");
        }
    }

    private static Path getClassDir() throws URISyntaxException, IOException {
        File parent = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile();
        //Parent could be a JAR. Keep searching upwards if that's the case.
        while(!parent.isDirectory()){
            parent = parent.getParentFile();
        }
        parent = parent.getCanonicalFile();
        return parent.toPath();
    }

    public static void checkOrRestore(String task_title, String zip_url, Path zip_destination, String zip_hash, String exe_entry_name, Path exe_destination, String exe_hash) throws IOException{
        if(FileOps.existsAndMatchesHash(exe_destination, exe_hash)){
            System.out.println(task_title + " hash verified.");
        } else {
            System.out.print("Restoring " + task_title + "... ");
            System.out.flush();
            FileOps.powerDelete(exe_destination);
            if(FileOps.downloadAndVerify(zip_url, zip_destination, zip_hash)){
                try(ZipFile zip = new ZipFile(zip_destination.toFile())){
                    ZipEntry entry = zip.getEntry(exe_entry_name);
                    if(entry == null){
                        abort("Could not locate " + task_title + " EXE in ZIP source.");
                    }
                    try(InputStream input = zip.getInputStream(entry)){
                        try(OutputStream output = Files.newOutputStream(exe_destination)){
                            input.transferTo(output);
                        }
                    }
                }
                FileOps.powerDelete(zip_destination);
                if(FileOps.existsAndMatchesHash(exe_destination, exe_hash)){
                    System.out.println("Done.");
                } else {
                    abort("Unpacked " + task_title + " from ZIP source and found wrong hash. Cancelling.");
                }
            } else {
                abort("Could not download ZIP source for " + task_title + ". Cancelling.");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        //Validate and resolve input and output paths
        if(args.length != 2){
            abort("Wrong number of console arguments. Expecting two paths.");
        }
        input_parent = Path.of(args[0]).toAbsolutePath();
        if(!Files.exists(input_parent, LinkOption.NOFOLLOW_LINKS)){
            abort("Input path must be an existing directory, ZIP, or JAR.");
        }
        detectAndValidateZippedInput();
        output_parent = Path.of(args[1]).toAbsolutePath();
        System.out.println("Input and output paths ready.");

        //Check and possibly restore executable dependencies
        Path executables_dir = getClassDir().resolve("Dependencies");
        Path xbrz_exe = executables_dir.resolve("ScalerTest_Windows.exe");
        String xbrz_exe_hash = "34D9EAF5FBC93BC7B8A3B62431B6151541FF452265875964A2A0699A6368D2B6";
        Path ffmpeg_exe = executables_dir.resolve("ffmpeg.exe");
        String ffmpeg_exe_hash = "72A489ECCD008C2EC2C0A5856C5C75BC3D8BBFA90166C4566865C246445E6AA3";
        Path oxipng_exe = executables_dir.resolve("oxipng.exe");
        String oxipng_exe_hash = "35AE3980AB831AF64F4AECC98F69B81BFA146FA750A61D17EBBB12520128CBC8";
        FileOps.ensureFileParentChain(xbrz_exe);

        checkOrRestore("ScalerTest", "https://sourceforge.net/projects/xbrz/files/ScalerTest_1.2.zip/download",
            executables_dir.resolve("ScalerTest.zip"), "96BDDA377388EE27C5FC710157F6C79DA9F0BE632BCEF8224C31A288AE56E8A5",
            "ScalerTest_Windows.exe", xbrz_exe, xbrz_exe_hash
        );

        checkOrRestore("FFMPEG", "https://github.com/GyanD/codexffmpeg/releases/download/9.0.1/ffmpeg-9.0.1-essentials_build.zip",
            executables_dir.resolve("ffmpeg-9.0.1-essentials_build.zip"), "FEC81AE03971D9DD4BE3EBE02E263BD2EC1D789483F931BDBA5F5715E65DA2E9",
            "ffmpeg-9.0.1-essentials_build/bin/ffmpeg.exe", ffmpeg_exe, ffmpeg_exe_hash
        );

        checkOrRestore("OxiPNG", "https://github.com/oxipng/oxipng/releases/download/v10.2.1/oxipng-10.2.1-x86_64-pc-windows-msvc.zip",
            executables_dir.resolve("oxipng-10.2.1-x86_64-pc-windows-msvc.zip"), "7E940F83EE46874B73F53031F96A15834CB70B220AF27391FB06FE7B4DD798E1",
            "oxipng-10.2.1-x86_64-pc-windows-msvc/oxipng.exe", oxipng_exe, oxipng_exe_hash
        );
    }
}