package io.github.zopulus.coogle;

import java.util.*;

public final class ScopeRequestsTest {
    public static void main(String[] args) {
        Fake service = new Fake();
        ScopeRequests requests = new ScopeRequests();
        List<String> results = new ArrayList<>();
        requests.ensure(service, List.of("system", "wallet"), results::add);
        check(service.requested.equals(List.of("wallet")), "Already granted scope requested again");
        check(requests.pending(), "Request did not remain pending");
        requests.ensure(service, List.of("system", "wallet"), results::add);
        check(service.calls == 1, "Duplicate authorization request");
        service.done.accept("denied");
        check(!requests.pending() && results.equals(List.of("denied")), "Denial lost or treated as success");
        check(service.calls == 1, "Denial repeated authorization request");
        requests.ensure(service, List.of("system", "wallet"), results::add);
        check(service.calls == 2, "Explicit retry prevented");
        service.done.accept(null);
        check(results.get(1) != null, "Partial approval treated as complete");
        service.granted.add("wallet");
        requests.ensure(service, List.of("system", "wallet"), results::add);
        check(results.get(2) == null && service.calls == 2, "Granted scope still requested");
        requests.ensure(service, List.of("camera"), results::add);
        requests.cancel();
        service.granted.add("camera");
        service.done.accept(null);
        check(results.size() == 3 && !requests.pending(), "Stale callback applied after service disconnect");
        service.fail = true;
        requests.ensure(service, List.of("settings"), results::add);
        check(!requests.pending() && results.get(3) != null, "Service failure left request stuck");
        check(FeatureScopes.required(FeaturePolicy.WALLET).equals(List.of("system", "com.android.systemui", "com.google.android.apps.walletnfcrel")), "Wallet preview scope missing");
        check(FeatureScopes.required(FeaturePolicy.SEARCH_BAR).equals(List.of("system", "com.android.launcher")), "Search bar declaration or launcher scope missing");
        check(FeatureScopes.required(FeaturePolicy.LENS).equals(List.of("com.google.android.googlequicksearchbox", "com.oplus.camera")), "Existing Lens switch requests shortcut scope");
        check(FeatureScopes.required("action_button_lens").equals(List.of("com.google.android.googlequicksearchbox", "com.oplus.gesture")), "Shortcut Lens scope missing");
        System.out.println("PASS: scope grants, partial approval, denial, explicit retry, disconnect and deduplication");
    }
    static class Fake implements ScopeRequests.Service {
        Set<String> granted = new HashSet<>(List.of("system"));
        List<String> requested;
        java.util.function.Consumer<String> done;
        int calls; boolean fail;
        public List<String> scope() { if (fail) throw new IllegalStateException("dead"); return List.copyOf(granted); }
        public void request(List<String> value, java.util.function.Consumer<String> callback) { calls++; requested = value; done = callback; }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
