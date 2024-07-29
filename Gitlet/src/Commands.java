import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class Commands {

    private static final String CWD = System.getProperty("user.dir");
    private static final File gitlet = new File(CWD, ".gitlet");
    private static String branch;
    private static Map<String, String> stageIndex = new HashMap<>();
    private static Map<String, String> headIndex = new HashMap<>();


    static {
        if (gitlet.exists()) {
            try {
                Utils.checkSetup();

                branch = Branch.getBranch();

                stageIndex = Utils.readStageIndex();
                Utils.updateStageIndex(stageIndex);
                Utils.updateStageIndexFile(stageIndex);

                headIndex = Branch.getHeadIndex();

            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Init
    public static void init(File gitlet) throws IOException {
        if (gitlet.mkdirs()) {
            Utils.checkSetup();

            branch = "main";
            Branch.updateHead(branch);
            Branch.branch(branch);

            System.out.println("Initialized empty Git repository in " + gitlet.getPath());
        }
        else {
            System.err.println("Failed to initialize in " + gitlet.getPath());
        }
    }

    // Commit
    public static void commit(String message) throws IOException, NoSuchAlgorithmException {
        if (!(message.equals("Initial commit") && headIndex.isEmpty())) {
            if (stageIndex.isEmpty()) {
                System.out.println("Cannot commit empty gitlet repository");
                return;
            } else if (stageIndex.equals(headIndex)) {
                System.out.println("No change since last commit, no need to commit");
                return;
            }
        } else if (message.isEmpty() || message == null) {
            System.out.println("Cannot commit empty message");
            return;
        }
        Commit.commit(message, stageIndex);
    }

    // Status
    public static void status() throws IOException, NoSuchAlgorithmException {
        Branch.printBranches();

        File directory = new File(CWD);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file: files) {
                if (file.isFile()) {
                    String filePath = file.getPath();
                    // File is in stage index
                    if (stageIndex.containsKey(filePath)) {
                        // File is in head index
                        if (headIndex.containsKey(filePath)) {
                            // File is modified since last commit
                            if (!stageIndex.get(filePath).equals(headIndex.get(filePath))) {
                                System.out.println("Modified: " + file.getName());
                            }
                        } else {
                            System.out.println("New added file: " + file.getName());
                        }
                    }
                    // File is no in stage index but in head index
                    else if (!stageIndex.containsKey(filePath) && headIndex.containsKey(filePath)){
                        System.out.println("Removed: " + file.getName());
                    }
                    // File is untracked
                    else {
                        System.out.println("Untracked: " + file.getName());
                    }
                }
            }
        }
    }

    // Add
    public static void add(String CWD, String fileName) throws IOException, NoSuchAlgorithmException {
        String filePath = CWD + File.separator + fileName;
        File file = new File(filePath);

        if (file.exists()) {
            if (stageIndex.containsKey(filePath)) {
                System.out.println("File already added: " + filePath);
                return;
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            String hash = Utils.sha1(content);
            stageIndex.put(filePath, hash);
            System.out.println("Added " + filePath);

            Utils.createBlobFile(hash, content);

            Utils.updateStageIndexFile(stageIndex);

        } else {
            System.out.println("File does not exist.");
        }
    }

    // Log
    public static void log() throws IOException, ClassNotFoundException {
        Branch.log();
    }

    // Find
    public static void find(String message) throws IOException, ClassNotFoundException {
        Commit.findCommitMessage(message);
    }

    // Branch
    public static void branch(String branchName) throws IOException {
        Branch.branch(branchName);
    }

    // Checkout
    public static void checkout(String branchName) throws IOException, ClassNotFoundException {
        Branch.checkout(branchName, stageIndex);
    }

    // Rm
    public static void remove(String fileName) throws IOException {
        String filePath = CWD + File.separator + fileName;
        File file = new File(filePath);

        if (file.exists()) {
            if (!stageIndex.containsKey(filePath) && !headIndex.containsKey(filePath)) {
                System.out.println("No reason to remove: neither added nor committed");
            }
            else if (headIndex.containsKey(filePath)) {
                if (stageIndex.containsKey(filePath)) {
                    stageIndex.remove(filePath);
                    Utils.updateStageIndexFile(stageIndex);
                    System.out.println(filePath + " staged for removal");
                }
                else {
                    System.out.println(filePath + " already staged for removal");
                }
            }
            else {
                System.out.println(filePath + " removed from stage");
            }
        }
        else {
            System.out.println("File does not exist");
        }
    }

    // Rm-branch
    public static void removeBranch(String branchName) {
        Branch.removeBranch(branchName);
    }

    // Reset
    public static void reset(String commitHash) throws IOException, ClassNotFoundException {
        Branch.reset(commitHash, stageIndex);
    }

    // Merge
    public static void merge(String branchName) throws IOException, NoSuchAlgorithmException, ClassNotFoundException {
        Branch.merge(branchName);
    }
}
