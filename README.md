# openTCS-fleet

Fleet management system based on [openTCS](https://www.opentcs.org/), with a VDA5050 vehicle driver.

## Layout

| Folder | Content |
|---|---|
| `opentcs-6.2.0` | openTCS 6.2.0-SNAPSHOT: kernel, default dispatcher and router strategies, Operations Desk, Kernel Control Center, Model Editor. The Gradle build runs here. |
| `opentcs-commadapter-vda5050` | VDA5050 vehicle driver (VDA5050 1.1 and 2.0), based on [openTCS/opentcs-commadapter-vda5050](https://github.com/openTCS/opentcs-commadapter-vda5050) 0.23 (commit `fd1dace`). Built together with `opentcs-6.2.0` (see its `settings.gradle`). |

## Notable changes compared to upstream

Not a complete list.

- **Failed actions:** if a robot reports `actionStatus FAILED` for an action on the last step of a drive order:
  - the drive order becomes `FAILED` and the vehicle stops;
  - the transport order stays `BEING_PROCESSED`, and the Operations Desk shows a notification;
  - withdrawing the transport order releases the vehicle, and the order then ends as `FAILED`;
  - rerouting skips a failed drive order.
- **Action log:** the VDA5050 driver logs the action states of each drive order's last step: `Last step of order … completed, action states: […]`.
- **Loaded vehicles:** the VDA5050 2.0 driver sets the vehicle property `vehicle.loaded` from the robot state's `loads` (`havingGoods`). While `vehicle.loaded=true`, the dispatcher creates no automatic parking or recharge order for that vehicle.
- **Kernel defaults** (`opentcs-6.2.0/opentcs-kernel/src/main/resources/org/opentcs/kernel/distribution/config/opentcs-kernel-defaults-custom.properties`):
  - MQTT broker settings for the VDA5050 driver;
  - robot-type routing: the property `tcs:routingCostForward<group> = Infinity` blocks a path for that routing group.

## Build

Build only the modules you changed, then copy their jars into the kernel install:

```
cd opentcs-6.2.0
.\gradlew.bat :opentcs-kernel:test :opentcs-kernel:jar
```

1. Find the jars: each module puts them in `<module>\build\libs\`; the VDA5050 driver's are in `..\opentcs-commadapter-vda5050\build\libs\`.
2. Stop the kernel.
3. Copy the jars into `build\install\opentcs\opentcs-kernel\lib`.
4. Start the kernel again.

**Don't run the root `gradlew build` or `installDist` where a kernel install is in use.** Both regenerate `build\install` and delete the kernel's `data\` folder, which holds the plant model (`data\model.xml`).

On Windows, `.java` files use CRLF line endings; `spotlessCheck` fails otherwise.

## Run

The start scripts are in the install folders:
- `opentcs-kernel\startKernel.bat`
- `opentcs-kernelcontrolcenter\startKernelControlCenter.bat`
- `opentcs-operationsdesk\startOperationsDesk.bat`
- `opentcs-modeleditor\startModelEditor.bat`

The VDA5050 driver needs an MQTT broker at the address in the kernel configuration (default `127.0.0.1:1883`).
