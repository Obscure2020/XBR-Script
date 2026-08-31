import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

class Main {
    private static Path getClassDir() throws URISyntaxException, IOException {
        File parent = Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile();
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
                        System.out.println("ABORT.");
                        System.out.println();
                        System.out.println("Could not locate " + task_title + " EXE in ZIP source.");
                        System.exit(0);
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
                    System.out.println("ABORT.");
                    System.out.println();
                    System.out.println("Unpacked " + task_title + " from ZIP source and found wrong hash. Cancelling.");
                    System.exit(0);
                }
            } else {
                System.out.println("ABORT.");
                System.out.println();
                System.out.println("Could not download ZIP source for " + task_title + ". Cancelling.");
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path executables_dir = getClassDir().resolve("Dependencies");
        Path xbrz_exe = executables_dir.resolve("ScalerTest_Windows.exe");
        String xbrz_exe_hash = "34D9EAF5FBC93BC7B8A3B62431B6151541FF452265875964A2A0699A6368D2B6";
        Path ffmpeg_exe = executables_dir.resolve("ffmpeg.exe");
        String ffmpeg_exe_hash = "72A489ECCD008C2EC2C0A5856C5C75BC3D8BBFA90166C4566865C246445E6AA3";
        FileOps.ensureFileParentChain(xbrz_exe);

        checkOrRestore("ScalerTest", "https://sourceforge.net/projects/xbrz/files/ScalerTest_1.2.zip/download",
            executables_dir.resolve("ScalerTest.zip"), "96BDDA377388EE27C5FC710157F6C79DA9F0BE632BCEF8224C31A288AE56E8A5",
            "ScalerTest_Windows.exe", xbrz_exe, xbrz_exe_hash
        );

        checkOrRestore("FFMPEG", "https://github.com/GyanD/codexffmpeg/releases/download/9.0.1/ffmpeg-9.0.1-essentials_build.zip",
            executables_dir.resolve("ffmpeg-9.0.1-essentials_build.zip"), "FEC81AE03971D9DD4BE3EBE02E263BD2EC1D789483F931BDBA5F5715E65DA2E9",
            "ffmpeg-9.0.1-essentials_build/bin/ffmpeg.exe", ffmpeg_exe, ffmpeg_exe_hash
        );
    }
}