import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Branch {

    private static final String CWD = System.getProperty("user.dir");
    private static final File gitlet = new File(CWD, ".gitlet");

    private static final String REFS_DIR_PATH = ".gitlet" + File.separator + "refs";
    private static final String HEADS_DIR_PATH = REFS_DIR_PATH + File.separator + "heads";
    private static final String HEAD_PATH = ".gitlet" + File.separator + "HEAD";
    private static String branch;
    private static String headHash;
    private static Map<String, String> headIndex = new HashMap<>();

    static {
        if (gitlet.exists()) {
            try {
                branch = readBranch();
                headHash = readHeadHash(branch);
                headIndex = readHeadIndex(headHash);

                if (headIndex == null) {
                    headIndex = new HashMap<>();
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            System.out.println("Repository not initialized.");
            System.exit(1);
        }
    }

    public static String getBranch() {
        return branch;
    }
    public static String getHeadHash() {
        return headHash;
    }
    public static Map<String, String> getHeadIndex() {
        return headIndex;
    }

    private static String readBranch() throws IOException {
        File head = new File(HEAD_PATH);
        String line;
        if (head.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(head))) {
                line = reader.readLine();
                if (line != null) {
                    String separator = File.separator;
                    String[] parts = line.split(separator.equals("\\") ? "\\\\" : separator);
                    if (parts.length != 4) {
                        System.err.println("Invalid head reference in " + HEAD_PATH);
                    }
                    else {
                        return parts[3];
                    }
                }
                else {
                    System.err.println("Head is empty");
                }
            }
        }
        else {
            System.err.println(HEAD_PATH + " does not exist");
        }
        return "main";
    }

    private static Map<String, String> readHeadIndex(String headHash) throws IOException, ClassNotFoundException {
        if (headHash != null && !headHash.isEmpty()) {
            return Objects.requireNonNull(Commit.readCommit(headHash)).getIndex();
        }
        else {
            System.err.println("Empty head hash");
            return new HashMap<>();
        }
    }
    

    private static String readHeadHash(String branchName) throws IOException {
        String filePath = HEADS_DIR_PATH + File.separator + branchName;
        File headFile = new File(filePath);
        if (headFile.exists()) {
            return new String(Files.readAllBytes(headFile.toPath())).trim();
        }
        else {
            System.err.println(filePath + " does not exist");
        }
        return null;
    }

    public static void updateHead(String branchName) throws IOException {
        try (FileWriter writer = new FileWriter(HEAD_PATH, false)) {
            writer.write(HEADS_DIR_PATH + File.separator + branchName);
        }
    }

    public static void updateBranch(String commitHash) throws IOException {
        try (FileWriter writer = new FileWriter(HEADS_DIR_PATH + File.separator + branch, false)) {
            writer.write(commitHash);
        }
    }

    public static void printBranches() {
        System.out.println("===");

        File headsDir = new File(HEADS_DIR_PATH);
        File[] headFiles = headsDir.listFiles();

        if (headFiles != null) {
            for (File headFile: headFiles) {
                if (headFile.isFile()) {
                    String head = headFile.getName();
                    if (head.equals(branch)) {
                        System.out.println("*" + head);
                    }
                    else {
                        System.out.println(head);
                    }
                }
            }
        }

        System.out.println("===");
    }

    // Log
    public static void log() throws IOException, ClassNotFoundException {
        printBranches();

        System.out.println("===");
        String headHash = getHeadHash();
        while (headHash != null && !headHash.isEmpty()) {
            Commit commit = Commit.readCommit(headHash);
            System.out.println("Commit: " + headHash);
            System.out.println("Date: " + new Date(commit.getTimestamp()));
            System.out.println("Message: " + commit.getMessage());
            System.out.println("===");
            headHash = commit.getParent();
        }
    }

    // Branch
    public static void branch(String branchName) throws IOException {
        File branchFile = new File(HEADS_DIR_PATH + File.separator + branchName);
        if (!branchFile.exists()) {
            branchFile.createNewFile();
            if (headHash != null) {
                try (FileWriter writer = new FileWriter(branchFile, false)) {
                    writer.write(headHash);
                }
            }
            System.out.println("Created branch " + branchName);
        } else {
            System.out.println("Branch already exists.");
        }
    }

    // Checkout
    public static void checkout(String branchName, Map<String, String> stageIndex) throws IOException, ClassNotFoundException {
        File branchFile = new File(HEADS_DIR_PATH + File.separator + branchName);
        if (branchFile.exists()) {
            if (branch.equals(branchName)) {
                System.out.println("Already on branch " + branchName);
                return;
            }

            // Read branch head index
            String headHash = readHeadHash(branchName);
            Map<String, String> newHeadIndex = readHeadIndex(headHash);

            // Check for uncommitted change
            if (!stageIndex.equals(headIndex)) {
                System.out.println("Cannot switch branches. Uncommitted changes would be lost.");
                return;
            }

            // Update stage index to reflect the target branch
            Utils.updateStageIndexFile(newHeadIndex);

            // Update head reference
            branch = branchName;
            updateHead(branchName);
            System.out.println("Switched to branch " + branchName);

            // Update CWD to reflect the target branch's state
            Utils.updateCWD(newHeadIndex);

            // Remove tracked files that are not in the target branch
            for (String filePath : stageIndex.keySet()) {
                if (!newHeadIndex.containsKey(filePath)) {
                    Files.deleteIfExists(Paths.get(filePath));
                    System.out.println("Removed " + filePath);
                }
            }

        } else {
            System.out.println("Branch does not exist.");
        }
    }

    // Rm-branch
    // Todo: prevent if the target branch has not been merged
    public static void removeBranch(String branchName) {
        if (branchName.equals(branch)) {
            System.out.println("Cannot remove current branch");
        }
        else {
            String headPath = HEADS_DIR_PATH + File.separator + branchName;
            File head = new File(headPath);
            if (head.exists()) {
                head.delete();
                System.out.println("Branch " + branchName + " deleted");
            }
            else {
                System.out.println("Branch " + branchName + " does not exist");
            }
        }
    }

    // Reset
    // Todo: prevent this action from losing all the commits after the target commit
    public static void reset(String commitHash, Map<String, String> stageIndex) throws IOException, ClassNotFoundException {
        // Check for uncommitted change
        if (!stageIndex.equals(headIndex)) {
            System.out.println("Cannot switch branches. Uncommitted changes would be lost.");
            return;
        }

        Commit commit = Commit.readCommit(commitHash);

        // Update the working directory to match the reset commit
        Map<String, String> resetIndex = commit.getIndex();
        Utils.updateCWD(resetIndex);

        // Remove files not in the reset commit
        for (String filePath : headIndex.keySet()) {
            if (!resetIndex.containsKey(filePath)) {
                Files.deleteIfExists(Paths.get(filePath));
                System.out.println("Removed: " + filePath);
            }
        }

        // Update current index and branch
        Utils.updateStageIndexFile(resetIndex);

        updateBranch(commitHash);
        System.out.println("Reset to commit " + commitHash);
    }

    // Merge
    public static void merge(String branchName) throws IOException, ClassNotFoundException, NoSuchAlgorithmException {
        if (branch.equals(branchName)) {
            System.out.println("Already on branch " + branchName);
            return;
        }

        String branchHeadHash = readHeadHash(branchName);
        if (branchHeadHash == null) {
            System.out.println("Branch does not exist.");
            return;
        }
        
        String ancestorCommitHash = Commit.findAncestorCommitHash(headHash, branchHeadHash);
        // Read the commit of the branch to be merged
        Commit branchCommit = Commit.readCommit(branchHeadHash);
        // Read the merge base commit
        Commit ancestorCommit = Commit.readCommit(ancestorCommitHash);

        Map<String, String> newHeadIndex = Commit.threeWayMerge(ancestorCommit.getIndex(), headIndex, branchCommit.getIndex());

        // Create a new commit for the merge
        String message = "Merged branch " + branchName + " into " + branch;
        System.out.println(message);
        Commit.commit(message, newHeadIndex);

        // Update CWD
        Utils.updateCWD(newHeadIndex);

        // Remove files not in the merged commit
        for (String filePath : headIndex.keySet()) {
            if (!newHeadIndex.containsKey(filePath)) {
                Files.deleteIfExists(Paths.get(filePath));
                System.out.println("Removed: " + filePath);
            }
        }
    }
}
