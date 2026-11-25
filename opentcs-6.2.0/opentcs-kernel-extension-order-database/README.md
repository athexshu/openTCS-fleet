# Order database extension

Kernel extension that writes every transport order change to a PostgreSQL database. openTCS keeps transport orders only in memory. A kernel restart loses them, and finished orders are deleted after `orderpool.sweepAge` (24 h by default).

The kernel only puts changes into a queue; a separate thread writes them. A slow, stopped or wrong database never holds up the kernel:
- changes wait in the queue, up to `orderdatabase.queueCapacity`;
- the extension reconnects with growing pauses, from 5 s up to 60 s;
- the kernel log gets one warning per minute.

## Setup

1. Create the database once per PC: run `setup\create-database.bat`.
   - It asks for a new password for the login `fms_orders`, then for the `postgres` password.
   - It creates the login and the database `fms_orders`. The extension creates the tables itself.
2. Add to the kernel's `config\opentcs-kernel.properties`:
   ```
   orderdatabase.enable = true
   orderdatabase.password = <the password from step 1>
   ```
3. Restart the kernel. The log shows `Order database: connected.`

You can use an existing PostgreSQL login instead of `fms_orders`, e.g. `postgres`:
1. Create only the database: `CREATE DATABASE fms_orders;`
2. Add `orderdatabase.username = postgres` to the two lines above.

The default settings are in `config\opentcs-kernel-defaults-custom.properties`:

| Setting | Default |
|---|---|
| `orderdatabase.enable` | `false` |
| `orderdatabase.jdbcUrl` | `jdbc:postgresql://localhost:5432/fms_orders` |
| `orderdatabase.username` | `fms_orders` |
| `orderdatabase.password` | empty |
| `orderdatabase.queueCapacity` | `10000` |

If these settings are missing, the extension stays off and the kernel starts as usual.

A full rebuild (`installDist`) resets `config\opentcs-kernel.properties`. Add the two lines again afterwards.

## Tables

**`transport_orders`**: one row per order, with its latest values.

| Column(s) | Content |
|---|---|
| `name`, `type`, `state`, `intended_vehicle`, `processing_vehicle` | Order fields |
| `creation_time`, `finished_time`, `deadline` | Kernel times (UTC); NULL if not set. openTCS sets `finished_time` only for FINISHED |
| `final_state_time` | When the order ended as FINISHED, FAILED or UNROUTABLE, taken from the kernel's history |
| `dispensable`, `wrapping_sequence`, `peripheral_reservation_token` | Order fields |
| `current_drive_order_index`, `current_route_step_index` | Progress |
| `properties` (jsonb) | Order properties |
| `dependencies` (jsonb) | Names of the orders this order depends on |
| `drive_orders` (jsonb) | Each drive order: `destination`, `operation`, `state`, `properties` and `route` (costs and steps) |
| `history` (jsonb) | The kernel's own history entries: time, event and supplement |
| `last_change_time` | Time of the last change |
| `removed_from_kernel_time` | When the kernel deleted the order. The row stays in the database. |

**`transport_order_changes`**: one row per change.

| Column | Content |
|---|---|
| `order_name`, `change_time` | Which order, and when |
| `change_type` | `CREATED`, `MODIFIED`, `REMOVED`, or `SNAPSHOT` (the order already existed when recording started) |
| `state`, `processing_vehicle`, `current_drive_order_index`, `drive_order_states` | Values after the change |
| `changes` | What changed, e.g. `state: DISPATCHABLE -> BEING_PROCESSED; processingVehicle: - -> Vehicle-01` |

A change of the current route step alone only updates `transport_orders`.

## Example queries

```sql
-- Latest orders
SELECT name, state, processing_vehicle, creation_time, finished_time
FROM transport_orders ORDER BY creation_time DESC LIMIT 20;

-- Timeline of one order
SELECT change_time, change_type, state, changes
FROM transport_order_changes WHERE order_name = 'TOrder-...' ORDER BY id;

-- Orders with a failed drive order
SELECT name, state, processing_vehicle
FROM transport_orders WHERE drive_orders @> '[{"state": "FAILED"}]';
```
