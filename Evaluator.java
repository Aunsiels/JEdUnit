import java.util.function.*;
import java.util.stream.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * Basic evaluator for automatic evaluation of programming excercise assignments.
 * This evaluator is intended to be used with Moodle and VPL (Virtual Programming Lab).
 * It provides basic capabilities to evaluate programming assignents.
 * 
 * !!! Normally there is no need to touch this file !!!
 * !!! Keep it, unless you are perfectly knowing what you are doing !!!
 * 
 * @author Nane Kratzke
 */
public class Evaluator {

    class Inspector {

        private Class object;

        public Inspector(String cname) throws ClassNotFoundException { this.object = Class.forName(cname); }

        public String getName() { return object.getSimpleName(); }

        public Stream<Field> fields() { 
            return Stream.of(object.getDeclaredFields()).filter(f -> {
                int m = ((Field)f).getModifiers();
                return !(Modifier.isStatic(m) && Modifier.isFinal(m));
            });
        }
        
        public boolean hasNoFields() { return fields().count() == 0; }

        public Stream<Field> constants() { 
            return Stream.of(object.getDeclaredFields()).filter(f -> {
                int m = ((Field)f).getModifiers();
                return Modifier.isStatic(m) && Modifier.isFinal(m);
            });
        }

        public boolean hasNoConstants() { return constants().count() == 0; }

        public Stream<Method> methods() { return Stream.of(object.getDeclaredMethods()); }

        public boolean hasNoMethods() { return methods().count() == 0; }
    }

    /**
     * The maximum points for a VPL assignment.
     */
    private static final int MAX = 100;

    /**
     * The currently reached points for a VPL assignment.
     */
    private int points = 0;

    /**
     * Test case counter.
     * Declared static to count testcases consecutively across
     * different Check classes.
     */
    private static int testcase = 0;

    /**
     * Adds points for grading if a check is passed (wishful behavior).
     * A comment is printed whether the check was successfull or not.
     */
    protected final void grading(int add, String remark, Supplier<Boolean> check) {
        testcase++;
        try {
            if (check.get()) {
                this.points += add;
                System.out.println(comment("Check " + testcase + ": " + remark + " [OK] (" + add + " points)"));
            } else System.out.println(comment("Check " + testcase + ": " + remark + " [FAILED] (0 of " + add + " points)"));
        } catch (Exception ex) {
            System.out.println(comment("Check " + testcase + ": " + remark + " [FAILED due to " + ex + "] (0 of " + add + " points)"));
        }
    }

    /**
     * Deletes points for grading if a check is passed (unwishful behavior).
     * A comment is printed whether the check was successfull or not.
     */
    protected final void degrading(int del, String remark, Supplier<Boolean> check) {
        testcase++;
        try {
            if (check.get()) {
                this.points -= del;
                System.out.println(comment("Check " + testcase + ": " + remark + " [OK] (no subtraction)"));
            } else System.out.println(comment("Check " + testcase + ": " + remark + " [FAILED] (subtracted " + del + " points)"));
        } catch (Exception ex) {
            System.out.println(comment("Check " + testcase + ": " + remark + " [FAILED due to " + ex + "] (subtracted " + del + " points)"));
        }
    }

    protected final <T> boolean assure(String className, Predicate<Inspector> check) {
        try {
            return check.test(new Inspector(className));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Adds a VPL comment.
     */
    protected final String comment(String c) { return "Comment :=>> " + c; }

    /**
     * This method scans and invokes all methods starting with "test" to run the grading.
     */
    protected final void evaluate() {
        for (Method test : this.getClass().getDeclaredMethods()) {
            if (!test.getName().startsWith("test")) continue;
            try {
                test.invoke(this);
            } catch (Exception ex) {
                System.out.println("Test case " + test.getName() + " failed completely." + ex);
            } finally {
                points = points > MAX ? MAX : points;
                points = points < 0 ? 0 : points;
                System.out.println("Grade :=>> " + points);
            }
        }
    }

    /**
     * The main method calls the evaluation.
     */
    public static final void main(String[] args) {
        Checks checks = new Checks();
        checks.evaluate();
    }
}