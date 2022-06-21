package de.thl.jedunit;

import static de.thl.jedunit.DSL.comment;
import static de.thl.jedunit.DSL.t;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;

import io.vavr.Tuple2;

/**
 * Basic evaluator for automatic evaluation of programming excercise
 * assignments. This evaluator is intended to be used with Moodle and VPL
 * (Virtual Programming Lab). It provides basic capabilities to evaluate
 * programming assignents.
 * 
 * @author Nane Kratzke
 */
public class Evaluator {

    /**
     * Whether to execute public or private tests.
     */
    protected boolean runPublicTests = true;

    /**
     * Whether to stop the tests when fail or not.
     */
    protected boolean stopEarly = false;

    /**
     * The maximum points for a VPL assignment.
     */
    private static final int MAX = 100;

    /**
     * The currently total number of points points.
     */
    private double totalPoints = 0.0;

    /**
     * The currently maximum number of points points.
     */
    private double totalMaxPoints = 0.0;

    /**
     * The results of the latest executed testcase.
     */
    private final List<Tuple2<Integer, Integer>> results = new LinkedList<>();

    /**
     * Test case counter. Declared static to count testcases consecutively across
     * different Check classes.
     */
    private static int testcase = 0;

    /**
     * Contains the standard out (console output).
     * 
     * @since 0.2.1
     */
    protected PrintStream stdout = System.out;

    /**
     * Redirects standard out (console output) into a file "console.log". This is
     * used to isolate possible tainted submission console outputs from trusted
     * evaluation logic console outputs. Necessary to avoid console injection
     * attacks.
     * 
     * @since 0.2.1
     */
    protected PrintStream redirected = new PrintStream(new ByteArrayOutputStream());

    /**
     * Current points (truncated to [0, 100])
     * @return points [0, 100]
     */
    public int getPoints() {
        int report = (int)Math.round(this.totalPoints / this.totalMaxPoints * 100);
        report = report < 0 ? 0 : report;
        return report;
    }

    /**
     * Generates a TestSeries object that executes a series of tests.
     * @param data Test data
     * @return Object that executes tests on the test data.
     * @since 0.2.0
     */
    @SafeVarargs
    public final <T> TestSeries<T> test(T... data) {
        return new TestSeries<T>(this, data);
    }

    public final void grading(int p, String comment, boolean success) {
        grading(p, comment, () -> success, false);
    }

    /**
     * Adds points for grading if a check is passed (wishful behavior).
     * A comment is always printed whether the check was successfull or not.
     * @param p Points to add (on success)
     * @param comment Comment to show
     * @param check Condition to check (success)
     */
    public final void grading(int p, String comment, Supplier<Boolean> check) {
        grading(p, comment, check, false);
    }

    /**
     * Adds points for grading if a check is passed (wishful behavior).
     * A comment is always printed whether the check was successfull or not.
     * @param p Points to add (on success)
     * @param comment Comment to show
     * @param check Condition to check (success)
     * @param trusted Executed in a trusted environment
     */
    public final void grading(int p, String comment, Supplier<Boolean> check, boolean trusted) {
        testcase++;
        if (!trusted) reset();
        try {
            if (check.get()) {
                results.add(t(p, p));
                comment("Check " + testcase + ": [OK] " + comment + " (" + p + " points)", !this.runPublicTests);
            } else {
                results.add(t(0, p));
                comment("Check " + testcase + ": [FAILED] " + comment + " (0 of " + p + " points)", !this.runPublicTests);
            }
        } catch (Exception ex) {
            results.add(t(0, p));
            comment("Check " + testcase + ": [FAILED due to " + ex + "] " + comment + " (0 of " + p + " points)", !this.runPublicTests);
        }
        if (!trusted) redirect();
    }

    /**
     * Deletes percentage points (penalzing) if a check is passed (unwishful behavior).
     * A comment is only printed if the check indicates a violation.
     * Penalities are applied to the complete percentage even if launched from 
     * weighted checks.
     * @param penalty Percentage points to remove (on violation)
     * @param remark Comment to show
     * @param violation Violation condition to check
     * @return true, if penalized
     *         false, otherwise
     */
    public final boolean penalize(int penalty, String remark, Supplier<Boolean> violation) {
        reset();
        try {
            if (!violation.get()) {
                redirect();
                return false;
            }
            this.totalPoints -= penalty;
            comment(String.format("[FAILED] %s (-%d%% on total result)", remark, penalty), !this.runPublicTests);
            redirect();
            return true;
        } catch (Exception ex) {
            comment("[FAILED due to " + ex + "] " + remark, !this.runPublicTests);
            redirect();
            return true;
        }
    }

    /**
     * Checks whether a severe condition is met. E.g. a cheating submission.
     * In case the check is evaluated to true the evaluation is aborted immediately.
     */
    protected final void abortOn(String comment, Supplier<Boolean> violation) {
        reset();
        try {
            if (!violation.get()) {
                redirect();
                return;
            }
            comment("Evaluation aborted! " + comment, !this.runPublicTests);
            this.totalPoints = 0;
            if (REALWORLD) {
                grade();
                System.exit(1);
            }
        } catch (Exception ex) {
            comment("[FAILED due to " + ex + "] " + comment, !this.runPublicTests);
        }
        redirect();
    }

    /**
     * Reports the current points to VPL via console output (truncated to [0, 100]).
     */
    public boolean grade() {
        reset();
        comment(String.format("Current number of points: %.0f%%", this.totalPoints / this.totalMaxPoints * 100.0),
                !this.runPublicTests);
        System.out.println("Grade :=>> " + this.getPoints());
        redirect();
        return true;
    }

    /**
     * Reports current results to VPL via console output (truncated to [0, 100]).
     * @param results List of results [(n, of possible points)]
     * @param weight Weight to be considered for total sum
     */
    public boolean grade(double weight, List<Tuple2<Integer, Integer>> results) {
        reset();
        if (results.isEmpty() || weight <= 0.0) {
            comment("No results or weight for this test", !this.runPublicTests);
            grade();
            redirect();
            return false;
        }
        int points = results.stream().map(d -> d._1).reduce(0, (a, b) -> a + b);
        int total = results.stream().map(d -> d._2).reduce(0, (a, b) -> a + b);
        double p = 100.0 * points / total;
        comment(String.format("Result for this test: %d of %d points (%.0f%%)", points, total, p),
                !this.runPublicTests);
        this.totalPoints += (weight * points) / total;
        this.totalMaxPoints += weight;
        grade();
        redirect();
        return points == total;
    }

    private List<Method> allMethodsOf(Class<?> clazz) {
        if (clazz == null) return new LinkedList<>();
        List<Method> methods = allMethodsOf(clazz.getSuperclass());
        methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        return methods;
    }

    private static int compareTwoTestMethods(Method m1, Method m2){
        Test t1 = m1.getAnnotation(Test.class);
        Test t2 = m2.getAnnotation(Test.class);
        double firstComp = t2.priority() - t1.priority();
        if (firstComp == 0) {
            return m1.getName().compareTo(m2.getName());
        } else if (firstComp < 0){
            return -1;
        } else {
            return 1;
        }
    }

    /**
     * Executes all methods annoted with a Test annotation.
     * Methods are executed according to their alphabetical order.
     * Standard out of submissions is redirected to file called console.log;
     */
    public final void runTests() {
        reset();
        allMethodsOf(this.getClass())
            .stream()
            .filter(method -> method.isAnnotationPresent(Test.class))
            .filter(method -> method.getAnnotation(Test.class).isPublic() == this.runPublicTests)
            .sorted((m1, m2) -> compareTwoTestMethods(m1, m2))
            .forEach(method -> {
                try {
                    Test t = method.getAnnotation(Test.class);
                    comment(String.format("- [%.2f%%]: ", t.weight() * 100) + t.description(), !this.runPublicTests);
                    results.clear();
                    // To prevent console injection attacks console output is redirected
                    redirect();
                    method.invoke(this);
                    reset(); // Resetting from console (stdout) redirection
                    boolean allGood = grade(t.weight(), results);
                    if (!allGood && this.stopEarly){
                        comment("The last test failed and the test pipeline was interrupted.", !this.runPublicTests);
                        throw new RuntimeException("The last test failed and the test pipeline was interrupted.");
                    }
                } catch (Exception ex) {
                    reset();
                    comment("Test '" + method.getName() + "' failed completely." + ex, !this.runPublicTests);
                    grade();
                }
                results.clear();
            });    
    }

    /**
     * Executes all methods annoted with an Inspection annotation.
     * Methods are executed according to their alphabetical order.
     */
    public final void runInspections() {
        allMethodsOf(this.getClass())
            .stream()
            .filter(method -> method.isAnnotationPresent(Inspection.class))
            .filter(method -> method.getAnnotation(Inspection.class).isPublic() == this.runPublicTests)
            .sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
            .forEach(method -> {
                try {
                    results.clear();
                    Inspection i = method.getAnnotation(Inspection.class);
                    comment("- " + i.description(), !this.runPublicTests);
                    method.invoke(this);
                    //grade();
                    comment("", !this.runPublicTests);
                } catch (Exception ex) {
                    comment("Inspection '" + method.getName() + "' failed completely." + ex, !this.runPublicTests);
                    //grade();
                }
                results.clear();
            });
    }

    /**
     * Indicates whether JEdUnit runs in the real world (true)
     * or under unit test conditions (false)
     */
    public static boolean REALWORLD = true;

    /**
     * This method evaluates the checkstyle log file.
     */
    public final void checkstyle() {
        try {
            comment("- Checkstyle");
            Scanner in = new Scanner(new File("checkstyle.log"));
            while (in.hasNextLine()) {
                String result = in.nextLine();
                for (String file : Config.EVALUATED_FILES) {
                    if (!result.contains(file)) continue;
                    if (Config.CHECKSTYLE_IGNORES.stream().anyMatch(ignore -> result.contains(ignore))) continue;

                    String msg = result.substring(result.indexOf(file));
                    comment(msg, !this.runPublicTests);
                    this.totalPoints -= Config.CHECKSTYLE_PENALTY;
                }
            }
            in.close();
            if (this.totalPoints >= 0) comment("Everything fine", !this.runPublicTests);
            if (this.totalPoints < 0) {
                String msg = String.format("[CHECKSTYLE] Found violations (%d%%)", (int)(this.totalPoints));
                comment(msg, !this.runPublicTests);
            }
            grade();
        } catch (Exception ex) {
            comment("You are so lucky! We had problems processing the checkstyle.log.", !this.runPublicTests);
            comment("This was due to: " + ex, !this.runPublicTests);
            grade();
        }
    }

    /**
     * Creates a file called console.log that stores all console output generated by the submitted logic.
     * Used to isolate the evaluation output from the submitted logic output to prevent injection attacks.
     * @since 0.2.1
     */
    public void initStdOutRedirection() throws Exception {
        redirected = new PrintStream(Config.STD_OUT_REDIRECTION);
    }

    /**
     * Redirects stdout to a file to isolate evaluation logic console output
     * from possibly tainted submission logic output.
     * @since 0.2.1
     */
    protected void redirect() {
        if (!this.runPublicTests) {
            System.setOut(redirected);
        }
    }

    /**
     * Resets stdout to "normal" console stream used by the evaluation logic output.
     * @since 0.2.1
     */
    protected void reset() {
        System.setOut(stdout);
    }

    /**
     * Runs the evaluation.
     * @param args command line options (not evaluated)
     */
    public static final void main(String[] args) {
        try {
            Constraints check = (Constraints)Class.forName("Checks").getDeclaredConstructor().newInstance();
            for (String arg: args) {
                if (arg.toLowerCase().equals("private")) {
                    check.runPublicTests = false;
                }
                if (arg.toLowerCase().equals("stopearly")) {
                    check.stopEarly = true;
                }
            }
            check.initStdOutRedirection();
            //comment("JEdUnit " + Config.VERSION, !this.runPublicTests);
            comment("", !check.runPublicTests);
            check.configure();
            if (Config.CHECKSTYLE) check.checkstyle();
            comment("", !check.runPublicTests);
            check.runInspections();
            check.runTests();
            comment(String.format("Finished: %d points", check.getPoints()), !check.runPublicTests);
        } catch (Exception ex) {
            comment("Severe error: " + ex, false);
        }
    }
}