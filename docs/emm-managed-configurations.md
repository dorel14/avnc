# Managed configurations (EMM/MDM)

AVNC supports [managed configurations](https://developer.android.com/work/managed-configurations),
allowing an EMM/MDM solution to enforce settings, lock parts of the app, and provision servers
on managed devices.

The schema is declared in [`app_restrictions.xml`](../app/src/main/res/xml/app_restrictions.xml).
Most EMM consoles use it to show a form, with sections, lists and switches.
If your EMM only offers a key/value or JSON editor, use the keys & values documented below.
Changes pushed by the EMM are applied immediately, without restarting the app.

## How values work

- **"Defined by user"**: Every setting defaults to an empty value (or `0` for numbers),
  which leaves it to the user. Any other value is enforced: it takes precedence over the
  user preference, and the setting is disabled in AVNC, with the summary "Managed by your organization".
- **Booleans** are `choice` restrictions with values `true` / `false` (plus the empty value),
  so that "Defined by user" can be selected. Locks are the only plain `bool` restrictions.
- **Sections**: Restrictions are grouped in `bundle` restrictions matching AVNC Settings screens
  (`interface`, `viewer`, `input`, `servers`, `tools`). Groups are only used for presentation,
  so AVNC also accepts a flat configuration (e.g. `gesture_style` at top level).
  Keys are unique across all sections.
- **Invalid values** are ignored, as if the setting was "Defined by user".

## Preferences

Restriction keys are the same as preference keys.
Per-server settings (e.g. orientation set in a server profile) still override these global values.

### Interface (`interface`)

| Key                      | Setting in AVNC                       | Values                                  |
|--------------------------|---------------------------------------|-----------------------------------------|
| `theme`                  | Theme                                 | `system`, `dark`, `light`               |
| `bell_enabled`           | Bell                                  | `true`, `false`                         |
| `sort_server_list`       | Sort saved servers by name            | `true`, `false`                         |
| `prefer_advanced_editor` | Directly open advanced server options | `true`, `false`                         |

### Viewer (`viewer`)

| Key                  | Setting in AVNC | Values                                                     |
|----------------------|-----------------|------------------------------------------------------------|
| `viewer_orientation` | Screen orientation | `auto` (Automatic), `portrait`, `landscape`                |
| `fullscreen_display` | Fullscreen      | `true`, `false`                                            |
| `keep_screen_on`     | Keep screen ON  | `true`, `false`                                            |

### Input (`input`)

Input settings are grouped in four sub-sections, like in AVNC Settings.

Gesture actions use these values:

| Value                | Meaning                 |
|----------------------|-------------------------|
| `none`               | None                    |
| `double-click`       | Double-click            |
| `middle-click`       | Middle-click            |
| `right-click`        | Right-click             |
| `left-press`         | Long-press (left button held down) |
| `open-keyboard`      | Open keyboard           |
| `pan`                | Pan local frame         |
| `remote-scroll`      | Scroll remote content   |
| `remote-drag`        | Drag remote content     |
| `remote-drag-middle` | Drag with middle button |
| `remote-back-press`  | Back-press on server    |
| `default`            | Default (Android behavior) |

#### Gestures (`input_gestures`)

| Key                         | Setting in AVNC           | Values                                                               |
|-----------------------------|---------------------------|----------------------------------------------------------------------|
| `gesture_style`             | Gesture style             | `touchscreen`, `touchpad`                                            |
| `gesture_double_tap`        | Double tap                | `none`, `double-click`, `middle-click`, `right-click`                |
| `gesture_long_press`        | Long press                | `none`, `double-click`, `middle-click`, `right-click`, `left-press`  |
| `gesture_tap2`              | Two-finger tap            | `none`, `right-click`, `open-keyboard`                               |
| `gesture_tap3`              | Three-finger tap          | `none`, `right-click`, `middle-click`                                |
| `gesture_swipe1`            | One-finger swipe          | `none`, `pan`, `remote-scroll`, `remote-drag`                        |
| `gesture_swipe2`            | Two-finger swipe          | `none`, `pan`, `remote-scroll`                                       |
| `gesture_swipe3`            | Three-finger swipe        | `none`, `pan`, `remote-scroll`                                       |
| `gesture_double_tap_swipe`  | Double tap and swipe      | `none`, `remote-drag`, `remote-drag-middle`, `pan`, `remote-scroll`  |
| `gesture_long_press_swipe`  | Long press and swipe      | `none`, `remote-drag`, `remote-drag-middle`, `pan`, `remote-scroll`  |
| `gesture_swipe_sensitivity` | Swipe sensitivity         | Integer from `5` to `15` (`10` is the AVNC default). `0` = defined by user |
| `invert_vertical_scrolling` | Invert vertical scrolling | `true`, `false`                                                      |

#### Mouse (`input_mouse`)

| Key                  | Setting in AVNC    | Values                                                         |
|----------------------|--------------------|----------------------------------------------------------------|
| `mouse_passthrough`  | Mouse passthrough  | `true`, `false`                                                |
| `capture_pointer`    | Capture pointer    | `true`, `false`                                                |
| `hide_local_cursor`  | Hide local pointer | `true`, `false`                                                |
| `hide_remote_cursor` | Hide remote pointer | `true`, `false`                                                |
| `mouse_back`         | Back button        | `default`, `middle-click`, `right-click`, `remote-back-press`  |

#### Virtual keys (`input_virtual_keys`)

| Key                            | Setting in AVNC              | Values          |
|--------------------------------|------------------------------|-----------------|
| `vk_open_with_keyboard`        | Open with keyboard           | `true`, `false` |
| `vk_use_super_with_single_tap` | Use Super key with single tap | `true`, `false` |
| `vk_row_count`                 | Rows                         | `1`, `2`, `3`   |

Custom virtual keys layout ("Customize virtual keys") is not supported, as it uses an internal format.

#### Key mapping (`input_key_mapping`)

| Key                           | Setting in AVNC        | Values          |
|-------------------------------|------------------------|-----------------|
| `km_right_alt_to_super`       | Right Alt → Super      | `true`, `false` |
| `km_language_switch_to_super` | Language Switch → Super | `true`, `false` |
| `km_back_to_escape`           | Back → Escape          | `true`, `false` |

### Servers (`servers`)

| Key                 | Setting in AVNC          | Values          |
|---------------------|--------------------------|-----------------|
| `auto_reconnect`    | Reconnect automatically  | `true`, `false` |
| `lock_saved_server` | Lock saved servers       | `true`, `false` |

## Locks

These are `bool` restrictions, `false` by default.

| Key                  | Section     | Effect when `true`                                                                 |
|----------------------|-------------|------------------------------------------------------------------------------------|
| `lock_settings`      | `interface` | All preferences are disabled in Settings. Preferences contained in imported files are ignored. |
| `lock_servers`       | `servers`   | Servers can't be added, edited, duplicated, deleted or imported. `vnc://` URIs can still be used to connect, but are never saved. |
| `lock_import_export` | `tools`     | Import/Export (file & QR code) is disabled. Also implied by `lock_servers`.        |

Connecting to servers is always allowed. Runtime state of a server (e.g. zoom level) is still saved.

## Managed servers

`managed_servers` (`bundle_array`) adds servers to the saved server list.
Each entry is a bundle with the following keys.

| Key             | Type    | Values / Notes                                                                 |
|-----------------|---------|--------------------------------------------------------------------------------|
| `id`            | string  | **Required.** Unique and stable identifier, used to keep servers in sync.      |
| `host`          | string  | **Required.** Hostname or IP address.                                          |
| `name`          | string  | Display name. Defaults to `host`.                                              |
| `port`          | integer | `1` to `65535`. Default `5900`.                                                |
| `username`      | string  | VNC username, if required by the security type.                                |
| `security_type` | choice  | `0` Automatic (default), `1` None, `2` VncAuth, `18` AnonTLS, `19` VeNCrypt, `30` macOS |
| `view_only`     | bool    | If `true`, input is disabled, and user can't turn it on.                       |

SSH fields are grouped in an `ssh` sub-bundle ("SSH tunnel" in EMM consoles):

| Key             | Type    | Values / Notes                                                  |
|-----------------|---------|-----------------------------------------------------------------|
| `channel_type`  | choice  | `tcp` (default, direct connection) or `ssh-tunnel`              |
| `ssh_host`      | string  | Hostname or IP address of the SSH server.                       |
| `ssh_port`      | integer | `1` to `65535`. Default `22`.                                   |
| `ssh_username`  | string  | SSH username.                                                   |
| `ssh_auth_type` | choice  | `password` (default) or `key`                                   |

Optional fields are grouped in an `advanced` sub-bundle ("Advanced" in EMM consoles).
Like preferences, each one is either enforced, or left to the user ("Defined by user").

| Key                     | Setting in AVNC                       | Values                                         |
|-------------------------|---------------------------------------|------------------------------------------------|
| `gesture_style`         | Gesture style                         | `auto` (use global setting), `touchscreen`, `touchpad` |
| `screen_orientation`    | Orientation                           | `auto` (use global setting), `portrait`, `landscape` |
| `image_quality`         | Image quality                         | `0` (lowest) to `9` (highest)                  |
| `use_raw_encoding`      | Raw                                   | `true`, `false`                                |
| `resize_remote_desktop` | Resize remote session to local window | `true`, `false`                                |
| `button_up_delay`       | Send delayed click events             | `true`, `false`                                |
| `connect_on_app_start`  | Auto-connect when app starts          | `true`, `false`                                |
| `enable_wol`            | Wake-on-LAN                           | `true`, `false`                                |
| `wol_mac`               | MAC address                           | e.g. `AA:BB:CC:DD:EE:FF`. Empty = defined by user |
| `wol_broadcast_address` | Broadcast address                     | e.g. `192.168.1.255`. Empty = defined by user  |
| `wol_port`              | Wake-on-LAN port                      | `1` to `65535`. `0` = defined by user          |

Entries without `id` or `host`, and duplicate `id`s, are ignored. At most 500 entries are used.

Secrets (passwords & private keys) can't be provided by managed configuration.
User is asked for the password when connecting, and can choose to remember it.
For `ssh_auth_type = key`, user has to import the private key in server settings.

### Behavior

- Managed servers show a lock icon. They can't be deleted, but they can be
  duplicated (unless `lock_servers` is set). The duplicate is a regular server.
- Managed servers can be edited (unless `lock_servers` is set), but fields listed
  above are read-only, as well as enforced optional fields. Other settings (e.g.
  password, SSH private key, non-enforced optional fields) can be changed by user.
- Servers are synced on app start, and whenever managed configuration changes.
  The fields above are updated, while local state (zoom, gesture style, remembered
  password, etc.) is kept. Servers removed from the list are deleted.
- Managed servers are never exported. Imported servers are never managed, and
  "Delete current servers" during import keeps managed servers.
- A `vnc://` URI can be used to connect to a managed server, but it never modifies it.

## Example

This is an example in the Android Enterprise JSON format (`ManagedConfiguration` of the
Google Play EMM API), which is accepted by the JSON editor of several EMMs.
Some EMMs use their own format, but keys, types and values are always the same.

It enforces the dark theme and touchpad gestures, locks servers, and provisions one server
whose gesture style is enforced, while other advanced settings are left to the user.

```json
{
  "kind": "androidenterprise#managedConfiguration",
  "productId": "app:com.gaurav.avnc",
  "managedProperty": [
    { "key": "interface", "valueBundle": { "managedProperty": [
      { "key": "theme", "valueString": "dark" }
    ]}},
    { "key": "input", "valueBundle": { "managedProperty": [
      { "key": "input_gestures", "valueBundle": { "managedProperty": [
        { "key": "gesture_style", "valueString": "touchpad" },
        { "key": "gesture_swipe_sensitivity", "valueInteger": 12 }
      ]}}
    ]}},
    { "key": "servers", "valueBundle": { "managedProperty": [
      { "key": "lock_servers", "valueBool": true }
    ]}},
    { "key": "managed_servers", "valueBundleArray": [
      { "managedProperty": [
        { "key": "id", "valueString": "office-pc" },
        { "key": "name", "valueString": "Office PC" },
        { "key": "host", "valueString": "192.168.1.10" },
        { "key": "port", "valueInteger": 5900 },
        { "key": "security_type", "valueString": "2" },
        { "key": "advanced", "valueBundle": { "managedProperty": [
          { "key": "gesture_style", "valueString": "touchpad" }
        ]}}
      ]}
    ]}
  ]
}
```

## Testing

[Test DPC](https://github.com/googlesamples/android-testdpc) can be used to push managed
configurations to AVNC on a test device: *Managed configurations* → select AVNC →
*Load manifest restrictions*, then edit values and save.
Test DPC shows a generic key/value editor: sections are shown as nested bundles
("Configure" button), and values must be typed (e.g. `true`, `touchpad`).
