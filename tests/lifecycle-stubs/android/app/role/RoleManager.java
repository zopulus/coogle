package android.app.role;
public class RoleManager {
    public static final String ROLE_WALLET = "android.app.role.WALLET";
    public boolean isRoleAvailable(String role) { return ROLE_WALLET.equals(role); }
}
