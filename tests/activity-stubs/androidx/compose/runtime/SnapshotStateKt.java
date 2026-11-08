package androidx.compose.runtime;
public final class SnapshotStateKt {
    public static <T> MutableState<T> mutableStateOf$default(T initial, SnapshotMutationPolicy<T> policy, int mask, Object unused) {
        return new MutableState<>() {
            T value = initial;
            public T getValue() { return value; }
            public void setValue(T next) { value = next; }
        };
    }
}
