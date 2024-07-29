import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Commit implements Serializable {

    private static final String OBJECTS_DIR_PATH = ".gitlet" + File.separator + "objects";

    private String message;
    private long timestamp;
    private String parent;
    private Map<String, String> index;

    public Commit(String message, long timestamp, String parent, Map<String, String> index) {
        this.message = message;
        this.timestamp = timestamp;
        this.parent = parent;
        this.index = index;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getParent() {
        return parent;
    }

    public Map<String, String> getIndex() {
        return index;
    }

    // Commit
    public static void commit(String message, Map<String, String> stageIndex) throws IOException, NoSuchAlgorithmException {
        String parent = Branch.getHeadHash();
        Commit newCommit = new Commit(message, System.currentTimeMillis(), parent, stageIndex);

        Map<String, String> headIndex = Branch.getHeadIndex();
        for (Map.Entry<String, String> entry : stageIndex.entrySet()) {
            String filePath = entry.getKey();
            String currentHash = entry.getValue();

            if (!currentHash.equals(headIndex.get(filePath))) {
                File file = new File(filePath);
                String content = new String(Files.readAllBytes(file.toPath()));
                Utils.createBlobFile(currentHash, content);
            }
        }

        String commitHash = Utils.sha1(Utils.serialize(newCommit));
        saveCommit(commitHash, newCommit);

        Branch.updateBranch(commitHash);

        // Remove tracked files that are not in the target branch
        for (String filePath : headIndex.keySet()) {
            if (!stageIndex.containsKey(filePath)) {
                Files.deleteIfExists(Paths.get(filePath));
                System.out.println("Removed " + filePath);
            }
        }

        System.out.println("Committed: " + message + " (" + commitHash + ")");
    }

    // Read the commit from .gitlet/objects/commitHash
    public static Commit readCommit(String commitHash) throws IOException, ClassNotFoundException {
        File commitFile = new File(OBJECTS_DIR_PATH, commitHash);
        if (commitFile.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(commitFile))) {
                return (Commit) in.readObject();
            }
        } else {
            System.err.println("Commit does not exist: " + commitHash);
            return null;
        }
    }

    // Create commit file in .gitlet/objects/
    private static void saveCommit(String hash, Commit commit) throws IOException {
        File commitFile = new File(OBJECTS_DIR_PATH, hash);
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(commitFile))) {
            out.writeObject(commit);
        }
    }

    // Find
    public static void findCommitMessage(String message) throws IOException, ClassNotFoundException {
        boolean find = false;
        String hash = Branch.getHeadHash();

        while (hash != null && !hash.isEmpty()) {
            Commit commit = readCommit(hash);

            if (commit.getMessage().equals(message)) {
                if (!find) {
                    System.out.println("===");
                }
                System.out.println("Commit: " + hash);
                System.out.println("Date: " + new Date(commit.getTimestamp()));
                System.out.println("Message: " + commit.getMessage());
                System.out.println("===");
                find = true;
            }
            hash = commit.getParent();
        }

        if (!find) {
            System.out.println("Commit message \"" + message + "\" not found");
        }
    }

    // Find the nearest shared ancestor commit hash
    public static String findAncestorCommitHash(String commitHash1, String commitHash2) throws IOException, ClassNotFoundException {
        Set<String> ancestors1 = getAncestorsHash(commitHash1);
        Set<String> ancestors2 = getAncestorsHash(commitHash2);

        for (String ancestor : ancestors1) {
            if (ancestors2.contains(ancestor)) {
                return ancestor;
            }
        }
        return null; // No common ancestor found
    }

    private static Set<String> getAncestorsHash(String commitHash) throws IOException, ClassNotFoundException {
        Set<String> ancestors = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(commitHash);

        while (!queue.isEmpty()) {
            String currentHash = queue.poll();
            ancestors.add(currentHash);

            Commit currentCommit = Commit.readCommit(currentHash);
            if (currentCommit.getParent() != null) {
                queue.add(currentCommit.getParent());
            }
        }

        return ancestors;
    }

    // Three-way merge
    public static Map<String, String> threeWayMerge(Map<String, String> ancestorIndex, Map<String, String> headIndex, Map<String, String> branchIndex) throws IOException, NoSuchAlgorithmException {
        Map<String, String> newHeadIndex = new HashMap<>();

        // Handle files present in ancestor
        for (String filePath : ancestorIndex.keySet()) {
            String ancestorHash = ancestorIndex.get(filePath);
            String headHash = headIndex.get(filePath);
            String branchHash = branchIndex.get(filePath);

            boolean modifiedInHead = headHash != null && !headHash.equals(ancestorHash);
            boolean modifiedInBranch = branchHash != null && !branchHash.equals(ancestorHash);

            if (headHash == null && branchHash == null) {
                // File was removed in both branches
                // Do nothing, file remains absent
            } else if (headHash == null) {
                // File was removed in head but modified in branch
                newHeadIndex.put(filePath, branchHash);
            } else if (branchHash == null) {
                // File was removed in branch but modified in head
                newHeadIndex.put(filePath, headHash);
            } else if (!modifiedInHead && modifiedInBranch) {
                // File modified in branch but not in head
                newHeadIndex.put(filePath, branchHash);
            } else if (modifiedInHead && !modifiedInBranch) {
                // File modified in head but not in branch
                newHeadIndex.put(filePath, headHash);
            } else if (modifiedInHead && modifiedInBranch && headHash.equals(branchHash)) {
                // File modified in both branches in the same way
                newHeadIndex.put(filePath, headHash);
            } else if (modifiedInHead && modifiedInBranch && !headHash.equals(branchHash)) {
                // File modified in different ways
                String headFileContent = Utils.readFileContent(headHash);
                String branchFileContent = Utils.readFileContent(branchHash);
                String newHeadFileContent = "<<<<<<< HEAD\n" + headFileContent + "\n=======\n" + branchFileContent + "\n>>>>>>>";

                String newHeadFileHash = Utils.sha1(newHeadFileContent);
                Utils.createBlobFile(newHeadFileHash, newHeadFileContent);
                newHeadIndex.put(filePath, newHeadFileHash);
                System.out.println("Encountered a merge conflict for file: " + filePath);
            }
        }

        // Handle files only present in head
        for (String file : headIndex.keySet()) {
            if (!ancestorIndex.containsKey(file)) {
                newHeadIndex.put(file, headIndex.get(file));
            }
        }

        // Handle files only present in branch
        for (String file : branchIndex.keySet()) {
            if (!ancestorIndex.containsKey(file) && !headIndex.containsKey(file)) {
                newHeadIndex.put(file, branchIndex.get(file));
            }
        }

        return newHeadIndex;
    }
}