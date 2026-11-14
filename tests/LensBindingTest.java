package io.github.zopulus.coogle;
import java.lang.reflect.Method;
import java.util.List;
import com.google.apps.tiktok.account.AccountId;

public final class LensBindingTest {
    static class Gate {}
    static class RenamedCreate {
        RenamedCreate(AccountId account, Gate gate, boolean enabled, long mode) {}
        boolean renamed() { return false; }
    }
    static class RenamedTalk {
        RenamedTalk(Object provider, Object caller, Object scope, boolean one, boolean two, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Wrong { boolean renamed() { return false; } }
    static class UpdatedTalk {
        UpdatedTalk(Object provider, Object caller, Object scope, boolean one, boolean two,
                    boolean three, boolean four, boolean five, boolean six, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class LatestTalk {
        LatestTalk(Object provider, Object caller, Object scope, boolean one, boolean two,
                   boolean three, boolean four, boolean five, boolean six, boolean seven,
                   boolean eight, boolean nine, boolean ten, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class CurrentTalk {
        CurrentTalk(Object provider, Object caller, Object dependency, Object scope, boolean one, boolean two,
                    boolean three, boolean four, boolean five, boolean six, boolean seven,
                    boolean eight, boolean nine, boolean ten, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Talk1773 {
        Talk1773(Object provider, Object caller, Object dependency, Object scope, boolean one, boolean two,
                 boolean three, boolean four, boolean five, boolean six, boolean seven,
                 boolean eight, boolean nine, boolean ten, boolean eleven, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Talk1775 {
        Talk1775(Object provider, Object caller, Object dependency, Object scope, boolean one, boolean two,
                 boolean three, boolean four, boolean five, boolean six, boolean seven,
                 boolean eight, boolean nine, boolean ten, boolean eleven, boolean twelve, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Wrong1773Prefix {
        Wrong1773Prefix(Object provider, Object caller, Object dependency, boolean scope, boolean one, boolean two,
                        boolean three, boolean four, boolean five, boolean six, boolean seven,
                        boolean eight, boolean nine, boolean ten, boolean eleven, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class Wrong1773Flag {
        Wrong1773Flag(Object provider, Object caller, Object dependency, Object scope, boolean one, boolean two,
                      boolean three, boolean four, boolean five, boolean six, boolean seven,
                      boolean eight, boolean nine, boolean ten, int eleven, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class UnknownExpandedLayout {
        UnknownExpandedLayout(Object provider, Object caller, Object dependency, Object scope, boolean one, boolean two,
                              boolean three, boolean four, boolean five, boolean six, boolean seven,
                              boolean eight, boolean nine, boolean ten, boolean eleven, boolean twelve, boolean thirteen, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class WrongTalkFlag {
        WrongTalkFlag(Object provider, Object caller, Object scope, boolean one, boolean two,
                      boolean three, int four, boolean five, boolean six, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class UnknownTalkLayout {
        UnknownTalkLayout(Object provider, Object caller, Object scope, boolean one, boolean two,
                          boolean three, boolean four, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    static class MissingMode {
        MissingMode(Object provider, Object caller, Object scope, boolean one, boolean two,
                    boolean three, boolean four, boolean five, boolean six, Object state) {}
        boolean renamed() { return false; }
    }
    static class DuplicateCreate {
        DuplicateCreate(AccountId account, Gate gate, boolean enabled, long mode) {}
        boolean renamed() { return false; }
    }
    static class CombinedController {
        CombinedController(AccountId account, Gate gate, boolean enabled, long mode) {}
        CombinedController(Object provider, Object caller, Object scope, boolean one, boolean two, Object state) {}
        boolean renamed() { return false; }
        long mode() { return 0; }
    }
    public static void main(String[] args) throws Exception {
        Method create = RenamedCreate.class.getDeclaredMethod("renamed");
        Method talk = RenamedTalk.class.getDeclaredMethod("renamed");
        Method wrong = Wrong.class.getDeclaredMethod("renamed");
        Method[] result = LensBinding.select(List.of(wrong, talk, create), Gate.class);
        check(result != null && result[0].equals(create) && result[1].equals(talk), "Renamed targets not selected");
        check(LensBinding.isPair(create, talk) && !LensBinding.isPair(talk, create), "Cached target order not validated");
        Method updated = UpdatedTalk.class.getDeclaredMethod("renamed");
        Method[] updatedPair = LensBinding.select(List.of(wrong, updated, create), Gate.class);
        check(updatedPair != null && updatedPair[0].equals(create) && updatedPair[1].equals(updated),
                "Google 17.68 Talk layout not selected");
        check(LensBinding.isPair(create, updated) && !LensBinding.isPair(updated, create),
                "Updated cached target order not validated");
        Method latest = LatestTalk.class.getDeclaredMethod("renamed");
        Method[] latestPair = LensBinding.select(List.of(wrong, latest, create), Gate.class);
        check(latestPair != null && latestPair[0].equals(create) && latestPair[1].equals(latest),
                "Google 17.70 Talk layout not selected");
        check(LensBinding.isPair(create, latest) && !LensBinding.isPair(latest, create),
                "Latest cached target order not validated");
        Method current = CurrentTalk.class.getDeclaredMethod("renamed");
        Method[] currentPair = LensBinding.select(List.of(wrong, current, create), Gate.class);
        check(currentPair != null && currentPair[0].equals(create) && currentPair[1].equals(current),
                "Google 17.72 Talk layout not selected");
        check(LensBinding.isPair(create, current) && !LensBinding.isPair(current, create),
                "Current cached target order not validated");
        Method expanded = Talk1773.class.getDeclaredMethod("renamed");
        Method[] expandedPair = LensBinding.select(List.of(wrong, expanded, create), Gate.class);
        check(expandedPair != null && expandedPair[0].equals(create) && expandedPair[1].equals(expanded),
                "Google 17.73 Talk layout not selected");
        check(LensBinding.isPair(create, expanded) && !LensBinding.isPair(expanded, create),
                "Google 17.73 cached target order not validated");
        Method newest = Talk1775.class.getDeclaredMethod("renamed");
        check(LensBinding.isPair(create, newest), "Google 17.75 Talk layout not selected");
        check(!LensBinding.isPair(newest, create), "Google 17.75 cached pair order accepted");
        check(LensBinding.select(List.of(create, newest, expanded), Gate.class) == null,
                "Ambiguous 17.73/17.75 Talk controllers accepted");
        check(LensBinding.select(List.of(create, current, expanded), Gate.class) == null,
                "Ambiguous 17.72/17.73 Talk controllers accepted");
        check(LensBinding.select(List.of(create, latest, current), Gate.class) == null,
                "Ambiguous 17.70/17.72 Talk controllers accepted");
        check(LensBinding.select(List.of(create, updated, latest), Gate.class) == null,
                "Ambiguous 17.68/17.70 Talk controllers accepted");
        check(LensBinding.select(List.of(create, talk, updated), Gate.class) == null,
                "Ambiguous old/new Talk controllers accepted");
        for (Class<?> invalid : List.of(WrongTalkFlag.class, UnknownTalkLayout.class, MissingMode.class,
                Wrong1773Prefix.class, Wrong1773Flag.class, UnknownExpandedLayout.class))
            check(LensBinding.select(List.of(create, invalid.getDeclaredMethod("renamed")), Gate.class) == null,
                    "Unsupported Talk controller accepted: " + invalid.getSimpleName());
        check(LensBinding.select(List.of(create, talk, DuplicateCreate.class.getDeclaredMethod("renamed")), Gate.class) == null,
                "Ambiguous create accepted");
        check(LensBinding.select(List.of(create, wrong), Gate.class) == null, "Missing talk accepted");
        check(LensBinding.select(List.of(create, talk), Wrong.class) == null, "Wrong gate accepted");
        Method combined = CombinedController.class.getDeclaredMethod("renamed");
        check(LensBinding.select(List.of(combined), Gate.class) == null,
                "One method accepted as both Lens targets");
        System.out.println("PASS: old/new Lens layouts and cache order; ambiguous/missing/mismatched targets rejected");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
