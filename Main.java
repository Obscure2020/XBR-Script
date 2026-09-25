import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

class Main {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_BRIGHT_RED = "\u001B[91m";
    public static final String ANSI_BRIGHT_GREEN = "\u001B[92m";
    public static final String ANSI_BRIGHT_YELLOW = "\u001B[93m";
    public static final String ANSI_BRIGHT_BLUE = "\u001B[94m";
    public static final String ANSI_BRIGHT_PURPLE = "\u001B[95m";
    public static final String ANSI_BRIGHT_CYAN = "\u001B[96m";
    public static final String ANSI_BRIGHT_WHITE = "\u001B[97m";

    private static Path input_parent;
    private static Path output_parent;
    private static boolean input_is_zipped = false;
    private static Path xbrz_exe;
    private static Path ffmpeg_exe;
    private static Path oxipng_exe;

    // Private Methods

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

    private static void checkOrRestore(String task_title, String zip_url, Path zip_destination, String zip_hash, String exe_entry_name, Path exe_destination, String exe_hash) throws IOException{
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

    private static Path[] listProcedures(Path procedures_dir) throws IOException {
        ArrayList<Path> result = new ArrayList<>();
        try(Stream<Path> stream = Files.walk(procedures_dir)){
            stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.toString().toLowerCase().endsWith(".txt"))
                .forEachOrdered(p -> result.add(p));
        }
        return result.toArray(new Path[0]);
    }

    private static BufferedImage normalize(BufferedImage input){
        final int width = input.getWidth();
        final int height = input.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for(int y=0; y<height; y++){
            for(int x=0; x<width; x++){
                final int original = input.getRGB(x, y);
                final int alpha = original >>> 24;
                final int copy = (alpha == 0) ? 0 : original;
                result.setRGB(x, y, copy);
            }
        }
        return result;
    }

    private static boolean checkOutputRelativeExists(String relative_path) throws IOException {
        Path target = output_parent.resolve(relative_path);
        return Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
    }

    private static BufferedImage readOutputRelative(String relative_path) throws IOException {
        File target = output_parent.resolve(relative_path).toFile().getCanonicalFile();
        return normalize(ImageIO.read(target));
    }

    private static ProcedureVerb[] parseProcedure(Path procedure) throws Exception {
        ArrayList<ProcedureVerb> result = new ArrayList<>();
        HashSet<String> virtual_context = new HashSet<>();
        String[] lines = FileOps.loadStrippedTextFile(procedure);

        for(int i=0; i<lines.length; i++){
            String line = lines[i];
            if(line.isEmpty()) continue;
            long human_line = ((long) i) + 1;
            String[] chunks = line.split("\\s+");
            ProcedureVerb verb = null;

            switch(chunks[0]){
                case "//":
                    break;

                case "read":{
                    verb = new ReadVerb(human_line, chunks, virtual_context);
                    break;
                }

                case "write":{
                    verb = new WriteVerb(human_line, chunks, virtual_context);
                    break;
                }

                default: {
                    StringBuilder sb = new StringBuilder("On line ");
                    sb.append(human_line);
                    sb.append(": Unrecognized verb \"");
                    sb.append(chunks[0]);
                    sb.append("\".");
                    if(chunks[0].startsWith("//")){
                        sb.append(ANSI_BRIGHT_CYAN + " (Hint: Comments must start with \"" + ANSI_RESET + "// " + ANSI_BRIGHT_CYAN + "\", INCLUDING the space.)" + ANSI_RESET);
                    }
                    throw new ProcedureParseException(sb.toString());
                }
            }

            if(verb != null){
                result.add(verb);
            }
        }

        return result.toArray(new ProcedureVerb[0]);
    }

    // Public methods used by Verbs

    public static boolean checkInputRelativeExists(String relative_path) throws IOException {
        if(input_is_zipped){
            try(ZipFile zip = new ZipFile(input_parent.toFile())){
                ZipEntry entry = zip.getEntry(relative_path);
                return (entry != null) && (!entry.isDirectory());
            }
        } else {
            Path target = input_parent.resolve(relative_path);
            return Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
        }
    }

    public static BufferedImage readInputRelative(String relative_path) throws IOException {
        BufferedImage read_result = null;
        if(input_is_zipped){
            try(ZipFile zip = new ZipFile(input_parent.toFile());
                InputStream input_stream = zip.getInputStream(zip.getEntry(relative_path))){
                read_result = ImageIO.read(input_stream);
            }
        } else {
            File target = input_parent.resolve(relative_path).toFile().getCanonicalFile();
            read_result = ImageIO.read(target);
        }
        return normalize(read_result);
    }

    // Plan for public static void writeOutputRelative:
    // - Check if item already exists at destination path.
    // - If yes:
    //     - Read in image already present, and normalize it
    //     - Perform equivalence check to outgoing image
    //     - If equivalent, return. No further work to be done.
    //     - If not equivalent, fall through to following case.
    // - If no:
    //     - Create a new temporary work directory.
    //     - Write output image into work directory.
    //     - Perform FFMPEG re-encoding to sibling file in work directory. (Technique borrowed from SquooshPNG project)
    //     - Perform OxiPNG in-situ optimization on result from FFMPEG.
    //     - Ensure parent chain exists for destination path.
    //     - Power Copy the final result file to the destination path.
    //     - Power Delete the work directory.

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
        Path class_dir = getClassDir();
        Path executables_dir = class_dir.resolve("Dependencies");
        xbrz_exe = executables_dir.resolve("ScalerTest_Windows.exe");
        String xbrz_exe_hash = "34D9EAF5FBC93BC7B8A3B62431B6151541FF452265875964A2A0699A6368D2B6";
        ffmpeg_exe = executables_dir.resolve("ffmpeg.exe");
        String ffmpeg_exe_hash = "72A489ECCD008C2EC2C0A5856C5C75BC3D8BBFA90166C4566865C246445E6AA3";
        oxipng_exe = executables_dir.resolve("oxipng.exe");
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

        //Enumerate procedures
        System.out.println();
        Path procedures_dir = class_dir.resolve("Procedures");
        Path[] procedures = listProcedures(procedures_dir);
        if(procedures.length == 1){
            System.out.println(procedures.length + " procedure found.");
        } else {
            System.out.println(procedures.length + " procedures found.");
        }
        if(procedures.length < 1){
            return;
        }
        System.out.println();

        //Parse and Execute procedures
        int successful_procs = 0;
        int failed_parses = 0;
        int failed_runs = 0;
        for(Path path : procedures){
            ProcedureVerb[] verbs = null;
            try{
                verbs = parseProcedure(path);
            } catch (ProcedureParseException e){
                System.out.println(ANSI_BRIGHT_RED + "PROBLEM" + ANSI_RESET + " while parsing procedure " + ANSI_BRIGHT_YELLOW + procedures_dir.relativize(path).toString() + ANSI_RESET);
                System.out.println(e.getMessage());
                System.out.println();
                failed_parses++;
                continue;
            }
            System.gc();
            int attempts = 0;
            Exception last_seen = null;
            while((attempts >= 0) && (attempts < 5)){
                try{
                    ProcedureContext context = new ProcedureContext();
                    for(ProcedureVerb verb : verbs){
                        verb.Execute(context);
                    }
                    attempts = -1;
                } catch (Exception e){
                    last_seen = e;
                    attempts++;
                }
            }
            if(attempts < 0){
                successful_procs++;
            } else {
                System.out.println(ANSI_BRIGHT_RED + "PROBLEM" + ANSI_RESET + " while running procedure " + ANSI_BRIGHT_YELLOW + procedures_dir.relativize(path).toString() + ANSI_RESET);
                if(last_seen == null){
                    System.out.println(ANSI_BRIGHT_CYAN + "Null exception." + ANSI_RESET);
                } else {
                    System.out.println(last_seen.getMessage());
                }
                System.out.println();
                failed_runs++;
            }
        }

        if(successful_procs == 1){
            System.out.println(successful_procs + " procedure successfully parsed and executed.");
        } else if(successful_procs > 1){
            System.out.println(successful_procs + " procedures successfully parsed and executed.");
        }

        if(failed_parses == 1){
            System.out.println(failed_parses + " procedure encountered parse issues.");
        } else if(failed_parses > 1){
            System.out.println(failed_parses + " procedures encountered parse issues.");
        }

        if(failed_runs == 1){
            System.out.println(failed_runs + " procedure encountered execution issues.");
        } else if(failed_runs > 1){
            System.out.println(failed_runs + " procedures encountered execution issues.");
        }
    }
}