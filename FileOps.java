import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public class FileOps {

    private static MessageDigest digest;
    private static HexFormat hex = HexFormat.of().withUpperCase();

    static {
        digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e){
            digest = null;
        }
    }

    public static boolean powerDelete(Path target){
        if(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false;
        if(Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)){
            while(true){
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "rmdir", "/S", "/Q", target.toAbsolutePath().toString());
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process p = null;
                while(true){
                    try{
                        p = pb.start();
                        break;
                    } catch (IOException ioex) {}
                }
                if(p != null){
                    while(true){
                        try{
                            p.waitFor();
                            break;
                        } catch (InterruptedException iex) {}
                    }
                }
                if(Files.exists(target, LinkOption.NOFOLLOW_LINKS)){
                    try{
                        Thread.sleep(1000);
                    } catch (InterruptedException iex) {}
                } else {
                    break;
                }
            }
        } else {
            File target_file = target.toAbsolutePath().toFile();
            boolean result = target_file.delete();
            if(!result){
                while(target_file.exists()){
                    try{
                        Thread.sleep(1000);
                    } catch (InterruptedException iex) {}
                    result = target_file.delete();
                    if(result) break;
                }
            }
        }
        return true;
    }

    public static void ensureFileParentChain(Path target) throws FileNotFoundException {
        ArrayDeque<Path> parentChain = new ArrayDeque<>();
        Path parent = target.toAbsolutePath().getParent();
        while(parent != null){
            parentChain.addFirst(parent);
            parent = parent.getParent();
        }
        if(parentChain.isEmpty()){
            throw new FileNotFoundException("The path \"" + target.toAbsolutePath().toString() + "\" does not have any parents.");
        }
        if(!Files.exists(parentChain.getFirst(), LinkOption.NOFOLLOW_LINKS)){
            throw new FileNotFoundException("The path \"" + target.toAbsolutePath().toString() + "\" seems to be rooted at a nonexistent location.");
        }
        while((!parentChain.isEmpty()) && Files.exists(parentChain.getFirst(), LinkOption.NOFOLLOW_LINKS)){
            parentChain.removeFirst();
        }
        for(Path p : parentChain){
            File f = p.toFile();
            boolean result = f.mkdir();
            if(!result){
                while(!f.exists()){
                    try{
                        Thread.sleep(1000);
                    } catch (InterruptedException iex) {}
                    result = f.mkdir();
                    if(result) break;
                }
            }
        }
    }

    public static String getFileHash(Path target) throws IOException {
        if(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)){
            return null;
        }
        try(InputStream is = Files.newInputStream(target)){
            byte[] buffer = new byte[65536];
            int bytesRead;
            while((bytesRead = is.read(buffer)) != -1){
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        return hex.formatHex(hashBytes);
    }

    public static boolean existsAndMatchesHash(Path target, String hash) throws IOException {
        String result = getFileHash(target);
        if(result == null){
            return false;
        }
        return hash.strip().toUpperCase().equals(result);
    }

    public static boolean downloadAndVerify(String url, Path destination, String hash) throws IOException {
        ensureFileParentChain(destination);
        boolean success = false;
        int retries = 16;
        while(retries > 0) {
            powerDelete(destination);
            retries--;
            int response_code = -1;
            try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_3).followRedirects(HttpClient.Redirect.NORMAL).build()){
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
                response_code = response.statusCode();
            } catch (Exception e) {
                response_code = -1;
            }
            if(response_code != 200) continue;
            if(existsAndMatchesHash(destination, hash)){
                success = true;
                break;
            }
        }
        return success;
    }

}