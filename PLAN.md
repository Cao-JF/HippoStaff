# Grant System
A Luckperm based grant system for Minecraft servers, allowing administrators to easily manage player permissions and roles.

## Functions
- Duration-based grants: Admins can specify how long a grant should last, with automatic expiration.
- Group management: Easily assign and revoke groups to players, with support for multiple groups per player
- GUI interface: User-friendly GUI for managing grants, making it accessible even for those unfamiliar with command-line interfaces.
- Persistence: All grants are stored in a database, ensuring that they persist across server restarts and crashes.
- Notifications: Players receive notifications when they are granted or revoked a group, keeping them informed of their permissions status.
- Integration with Luckperm: Seamlessly integrates with the Luckperm plugin, allowing for easy management of permissions and groups.
- Audit logging: All grant actions are logged for auditing purposes, providing a clear record of who granted or revoked permissions and when.
- `hippostaff.grantable` permission node: Only group that have this permission node can be granted to players, ensuring that only appropriate groups are assignable through the system.
- With that permission node, there are only a few groups that can be granted, and they are all staff groups. This ensures that only staff members can be granted permissions through the system, and that players cannot accidentally grant themselves or others permissions that they should not have.
- Auto revoke when the duration expires: The system will automatically revoke the grant when the specified duration expires, ensuring that permissions are not left active indefinitely.
- Grant history: The system keeps a history of all grants and revocations for each player, allowing administrators to track changes over time and identify any potential issues or abuses of the system.
- Discord webhook integration: The system can be configured to send notifications to a Discord channel via webhook when grants are created, revoked, or expired, providing real-time updates to staff members and enhancing communication.
- Multi-server support: The system can be configured to work across multiple servers in a network, allowing for centralized management of grants and permissions across the entire network. (Luckperms already synced so only one command need to be run)

## Commands
- '/grant <player>': Opens a GUI to grant player certain group (`hippostaff.grantable` groups only)
- '/revoke <player>': Opens a GUI to revoke player certain group
- '/granthistory <player>': Displays a history of grants and revocations for the specified player, including timestamps and the admin responsible for each action.
- '/grantlist <player>': Displays a list of all active grants for the specified player, including the groups they belong to and the remaining duration of each grant.
