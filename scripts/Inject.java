import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/** Tiny X11 input injector for automated client testing: key/type/click/move. */
public final class Inject {
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(30);
        switch (args[0]) {
            case "key" -> {
                int code = Integer.parseInt(args[1]);
                press(robot, code);
            }
            case "type" -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(args[1]), null);
                robot.delay(150);
                robot.keyPress(KeyEvent.VK_CONTROL);
                press(robot, KeyEvent.VK_V);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            case "click" -> {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                robot.mouseMove(x, y);
                robot.delay(80);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(60);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "selectall" -> {
                robot.keyPress(KeyEvent.VK_CONTROL);
                press(robot, KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            case "backspace" -> {
                int n = args.length > 1 ? Integer.parseInt(args[1]) : 1;
                for (int i = 0; i < n; i++) press(robot, KeyEvent.VK_BACK_SPACE);
            }
            case "move" -> {
                robot.mouseMove(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            }
            case "scroll" -> {
                robot.mouseWheel(Integer.parseInt(args[1]));
            }
            default -> System.out.println("usage: key <code> | type <text> | click <x> <y> | move <x> <y>");
        }
        robot.delay(120);
    }

    private static void press(Robot robot, int code) {
        robot.keyPress(code);
        robot.delay(40);
        robot.keyRelease(code);
    }
}
