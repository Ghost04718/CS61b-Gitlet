import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

public class Utils {

    private static final String OBJECTS_DIR_PATH = ".gitlet" + File.separator + "objects";
    private static final String REFS_DIR_PATH = ".gitlet" + File.separator + "refs";
    // all branches
    private static final String HEADS_DIR_PATH = REFS_DIR_PATH + File.separator + "heads";
    private static final String HEAD_PATH = ".gitlet" + File.separator + "HEAD";

    private static final String STAGE_INDEX_PATH = ".gitlet" + File.separator + "stage_index";

    // Return true if secondArg exists, otherwise false
    public static boolean secondArg(String[] args) {
        if (args.length != 2) {
            System.err.println("Invalid command.");
            return false;
        }
        return true;
    }

    // Return the SHA-1 hash of a string
    public static String sha1(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] result = md.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : result) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    // Return the SHA-1 hash of bytes
    public static String sha1(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] result = md.digest(input);
        Formatter formatter = new Formatter();
        for (byte b : result) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    // Return the bytes of an object
    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    // Create a blob file, whose name is the hash
    public static void createBlobFile(String hash, String content) throws IOException {
        File blobFile = new File(OBJECTS_DIR_PATH, hash);
        if (!blobFile.exists()) {
            Files.write(blobFile.toPath(), content.getBytes(), StandardOpenOption.CREATE);
            System.out.println("Blob file created: " + blobFile.getAbsolutePath());
        }
    }

    // Check whether all dirs and files exist
    public static void checkSetup() throws IOException {
        checkDir(OBJECTS_DIR_PATH);
        checkDir(REFS_DIR_PATH);
        checkDir(HEADS_DIR_PATH);

        checkFile(STAGE_INDEX_PATH);
        checkFile(HEAD_PATH);
    }

    // Check whether a file exists
    public static void checkFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    // Check whether a dir exists
    public static void checkDir(String dirPath) {
        File file = new File(dirPath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    // Return the index from the index file
    public static Map<String, String> readIndexFile(String filePath) throws IOException {
        Map<String, String> index = new HashMap<>();
        File file = new File(filePath);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 2); // Split into at most 2 parts
                    if (parts.length == 2) {
                        index.put(parts[0], parts[1]);
                    } else {
                        System.err.println("Invalid entry in " + filePath + ": " + line);
                    }
                }
            }
        }
        else {
            System.err.println("Index file " + filePath + "does not exist");
        }
        return index;
    }

    public static Map<String, String> readStageIndex() throws IOException {
        return readIndexFile(STAGE_INDEX_PATH);
    }

    // Update the stage index file content
    public static void updateStageIndexFile(Map<String, String> stageIndex) throws IOException {
        FileWriter writer = new FileWriter(STAGE_INDEX_PATH, false);
        for (Map.Entry<String, String> entry : stageIndex.entrySet()) {
            String fp = entry.getKey();
            String h = entry.getValue();
            writer.write(fp + " " + h + "\n");
        }
        writer.close();
    }

    // Update the directory current state
    public static void updateStageIndex(Map<String, String> stageIndex) throws NoSuchAlgorithmException, IOException {
        for (Map.Entry<String, String> entry : stageIndex.entrySet()) {
            String filePath = entry.getKey();
            File file = new File(filePath);
            String content = new String(Files.readAllBytes(file.toPath()));
            String currentHash = Utils.sha1(content);

            stageIndex.put(filePath, currentHash);
        }

        updateStageIndexFile(stageIndex);
    }

    // Update CWD in response to a certain index
    public static void updateCWD(Map<String, String> newIndex) throws IOException {
        for (Map.Entry<String, String> entry : newIndex.entrySet()) {
            String filePath = entry.getKey();
            String fileHash = entry.getValue();

            Path objectPath = Paths.get(OBJECTS_DIR_PATH, fileHash);
            // Read content from the source file
            byte[] content = Files.readAllBytes(objectPath);

            // Write content to the destination file
            Path destinationPath = Paths.get(filePath);
            Files.write(destinationPath, content);

            // Check if the file is correctly written
            if (!Files.exists(destinationPath) || Files.size(destinationPath) != content.length) {
                System.err.println("Failed to update file: " + destinationPath);
            }
        }
    }

    public static String readFileContent(String fileHash) throws IOException {
        File file = new File(OBJECTS_DIR_PATH, fileHash);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
