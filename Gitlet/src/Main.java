import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, ClassNotFoundException {
        String CWD = System.getProperty("user.dir");
        File gitlet = new File(CWD, ".gitlet");

        if (args.length == 0) {
            System.exit(1);
        }
        String firstArg = args[0];

        if (!firstArg.equals("init")) {
            if (!gitlet.exists()) {
                System.out.println("Repository not initialized.");
                System.exit(1);
            }
        }

        switch (firstArg) {
            case "init":
                if (!gitlet.exists()) {
                    Commands.init(gitlet);
                    Commands.commit("Initial commit");
                }
                else {
                    System.out.println("Repository already initialized.");
                }
                break;
            case "status":
                Commands.status();
                break;
            case "add":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.add(CWD, secondArg);
                }
                break;
            case "commit":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.commit(secondArg);
                }
                break;
            case "log":
                Commands.log();
                break;
            case "branch":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.branch(secondArg);
                }
                break;
            case "checkout":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.checkout(secondArg);
                }
                break;
            case "rm":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.remove(secondArg);
                }
                break;
            case "rm-branch":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.removeBranch(secondArg);
                }
                break;
            case "merge":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.merge(secondArg);
                }
                break;
            case "reset":
                if (Utils.secondArg(args)) {
                    String secondArg = args[1];
                    Commands.reset(secondArg);
                }
                break;
        }
    }
}