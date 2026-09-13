# EMM/MDM Managed Server Profiles

## Overview

AVNC supports provisioning server profiles via EMM/MDM restrictions. An EMM console can push a list of server profiles that AVNC automatically injects into the saved servers list, keeps synchronized, and protects from manual modification.

## Restriction Schema

The restriction key `managed_servers` (type: `bundle_array`) contains a list of `Bundle` entries, each representing a server profile.

### Fields

| Key | Type | Description |
|---|---|---|
| `id` | string | Stable identifier (required). Used as diff key. |
| `name` | string | Display name. Falls back to `host` if empty. |
| `host` | string | Server host (required). |
| `port` | integer | Server port. Default: 5900. Clamped to [1, 65535]. |
| `security_type` | choice | Security type. Values match `profile_editor_security_values`. Default: 0 (auto). |
| `channel_type` | choice | `tcp` or `ssh-tunnel`. Default: `tcp`. |
| `ssh_host` | string | SSH host. |
| `ssh_port` | integer | SSH port. Default: 22. Clamped to [1, 65535]. |
| `ssh_username` | string | SSH username. |
| `ssh_auth_type` | choice | `key` or `password`. Default: `key`. |
| `username` | string | Connection username. |
| `view_only` | bool | If true, sets view mode to no-input. Default: false. |

### Security Policy

The following fields are **never** transmitted via EMM restrictions:
- `password` (VNC password)
- `sshPassword` (SSH password)
- `sshPrivateKey` (SSH private key)

SSH-managed profiles without a local key remain in a "SSH key required" state. The user can set a private key directly on the profile (same as for a local profile) by importing or providing it in the connection settings.

## Synchronization

### Startup

On app startup (`Application.onCreate`), AVNC reads `ManagedConfig.restrictions` directly (the `restrictionsChanged` LiveData does not emit an initial value) and triggers a sync.

### On Restriction Change

AVNC observes `ManagedConfig.restrictionsChanged` and triggers a sync on each broadcast.

### Sync Logic

1. Fetch existing managed profiles from DB (`SELECT * FROM profiles WHERE isManaged = 1`)
2. Parse incoming EMM bundles into `ServerProfile` objects (`isManaged = true`, `managedId` set)
3. For each incoming profile:
   - If `managedId` exists in DB: **update** (merge EMM-controlled fields, preserve local state)
   - If `managedId` is new: **insert**
4. For each existing managed profile not in incoming list: **delete** (including any locally attached private key)
5. Manual profiles (`isManaged = false`) are **never** affected

### Merge Behavior

On update, only EMM-controlled fields are overwritten:
- `name`, `host`, `port`, `securityType`, `channelType`, `sshHost`, `sshPort`, `sshUsername`, `sshAuthType`, `viewMode`

Preserved across sync:
- `ID`, `isManaged`, `managedId`, `useCount`, `sshPrivateKey`, zoom, gestures, flags, etc.

## Validation

- Empty `id` or `host` → entry skipped (warning logged)
- Duplicate `id` → first occurrence kept (warning logged)
- `port` or `ssh_port` out of range → fallback to default
- Unknown `security_type`, `channel_type`, `ssh_auth_type` → fallback to defaults
- More than 500 profiles → first 500 kept (warning logged)
- API < 23: `bundle_array` unavailable, sync no-ops (existing managed profiles preserved)

## UI Behavior

- Managed profiles appear in the server list alongside manual profiles
- A lock icon badge indicates managed status
- Long-press context menu on managed profiles: **no Edit/Delete**; **Duplicate** available
- Edit screen is blocked for managed profiles (read-only or blocked)
- Duplication of a managed profile creates an editable copy (`isManaged = false`, `managedId = null`)

## Export/Import

- **Export**: managed profiles are excluded from the export file
- **Import**: all imported profiles have `isManaged = false` and `managedId = null` forced
