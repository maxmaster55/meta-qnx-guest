# meta-qnx-guest

Images for QNX guests running under the hypervisor host. Policy only — every
mechanism comes from [meta-qnx](../meta-qnx), and the shared application recipes
come from [meta-qnx-hyp](../meta-qnx-hyp).

## What it builds

```bash
bitbake qnx-guest-image     # a bootable guest IFS: drivers + the SOME/IP runtimes
bitbake spi-loopback        # SPI loopback test
bitbake motor-ai-server     # CommonAPI/SOME-IP motor AI service (generators run at configure)
bitbake motor-ai-client     # ...and its client
bitbake qnx-screen-virtio   # guest-side virtio GPU driver stack (prebuilt .apk; staged, not yet imaged)
bitbake qtbase qtdeclarative # Qt 6.10.3 for QNX, from stock meta-qt6
bitbake qt-cluster          # the Qt Quick cluster app + its self-contained deploy tree
bitbake qnx-guest-rootfs    # rootfs.img: the guest's QNX6 data disk (carries the Qt payload)
```

Qt comes from **stock meta-qt6**, cross-compiled for QNX by `qnx-toolchain` plus the
bbappends in `meta-qnx/dynamic-layers/qt6-layer/` — see
[qt6.md](../meta-qnx/docs/qt6.md). `qtbase` brings QtCore/QtGui and the QNX platform
plugin (`libqqnx.so`, which links the SDP's `libscreen`); `qtdeclarative` brings the
QML/Quick stack. Host tools come from `qtbase-native`, so nothing is built by hand.

`qt-cluster` takes `DEPENDS = "qtbase qtdeclarative qtbase-native"` and its post-build
step leaves a relocatable `qt-cluster/` deploy tree (`run.sh`, `appCluster`, the Qt
libs/QML/plugins it uses). It contributes no IFS entries: the payload belongs on a
mounted filesystem, not in RAM — which is what `qnx-guest-rootfs` is for.

The SOME/IP stack beneath the motor-ai apps — `boost`, `vsomeip` (with the QNX routing
patch), `commonapi-core`, `commonapi-someip` and the native code generators — lives in
[`recipes-someip/`](recipes-someip/) and builds through plain `DEPENDS`.

## The guest data disk

An IFS is copied into guest RAM whole at boot and is read-only, so neither the Qt payload
nor **any of the applications** live in it. `qnx-guest-rootfs` builds `rootfs.img` — a bare
QNX6 filesystem (via meta-qnx's `qnx-rootfs` class) carrying `qt-cluster`'s deploy tree at
`/qt-cluster`, the graphics stack, and `motor_data_producer`, `motor_recorder`,
`motor_ai_client`, `motor_diag_service`, `fault_tester` and `spi_loopback`.

That last part is about iteration speed, not size: changing one binary inside the IFS costs
a full image rebuild and a reflash of the SD card, where on this disk it is one `scp` into
the running guest. The mount is a union, so `/bin`, `/usr/bin` and `/etc` here merge with
the IFS's and `start-guest1.sh` finds every name it always used. What stays in the IFS is
what runs *before* `.rootfs-mount.sh` — `rpi-gpio`, `spi-dwc`, the BSP drivers — plus the
libraries the applications link (`libmosquitto`, the SOME/IP runtime), which nobody
iterates on. [applications.md](../meta-qnx-hyp/docs/applications.md#why-the-applications-are-not-in-the-ifs)
has the full rule.

Three pieces wire the disk into a running guest, mirroring `qnx_guests/images/guest-1/`:

1. **The guest `.qvmconf`** attaches it as a `virtio-blk` vdev (`loc 0x1c0b0000`,
   `intr gic:45`, `hostdev rootfs.img`).
2. **The guest boot script** (`qnx-guest.build.in`) carries an inline `.rootfs-mount.sh`
   that starts `devb-virtio` against that vdev and `mount -t qnx6 /dev/vblk0 /` early, so
   `/qt-cluster/run.sh` resolves. A guest booted without the disk logs an error and
   continues.
3. **The `qnx-host-data` bbappend** places `rootfs.img` next to the guest IFS and
   `.qvmconf` at `/guests/guest-1/` on the host data partition, and switches that partition
   to `auto` sizing so it grows to hold the ~366 MB image.

Add anything else to the disk by adding it to `QNX_ROOTFS_INSTALL` in `qnx-guest-rootfs`.
A binary staged in the processor tree's `bin` or `usr/bin` needs nothing more — the
template maps both trees whole. Anything on no search path, such as a config file under
`/etc`, needs an explicit record there.

This layer also **bbappends `qnx-host-data`** (from meta-qnx-hyp) to place all of the guest
artifacts on the host's QNX6 data partition — the same paths the hypervisor project uses.
The append keeps the layer dependency pointing one way: a build without this layer just
produces a data partition with no guests.

## Why this layer is so small

A guest is loaded by qvm at `0x80000000` as ELF with the generic
`startup-armv8_fm`, which is exactly what meta-qnx defaults to. So unlike the
host — which overrides load address, image format, startup program and hardware
— **this image sets no boot configuration at all**. The only board-ish knob is
which virtio-console vdev the host handed it:

```bitbake
QNX_GUEST_CONSOLE = "0x20000000,42"   # must match the guest's .qvmconf
```

That is the clearest evidence that meta-qnx's split between mechanism and policy
is in the right place.

## Layer dependencies

`LAYERDEPENDS = "core qnx qnx-hyp"`.

The dependency on `meta-qnx-hyp` is for **applications, not the host image**:
several are built once and installed into both host and guests (`rpi-gpio`,
`motor-controller`). Duplicating those recipes per image layer would be worse than
depending on the layer that holds them. If the application recipes ever outgrow
that arrangement they should move to a layer of their own, with both image
layers depending on it.

## Application sources

Most recipes clone their own repository and track the branch head — see
[meta-qnx/docs/variables.md](../meta-qnx/docs/variables.md) for `QNX_SRC_*`.

| Recipe | Source |
| --- | --- |
| `spi-loopback` | `PM-Maestro-ITI-GP-Org/spi_loopback` |
| `motor-ai-server` | `PM-Maestro-ITI-GP-Org/motor_ai_server` |
| `motor-ai-client` | `PM-Maestro-ITI-GP-Org/motor_ai_client` |
| `motor-data-producer` | `Mintharah/SPI-Stm32-QNX` (branch `spi_qnx_build`) |
| `shm-chunker` | the hypervisor monorepo, via `QNX_PROJECT_SRC` — no standalone repo yet |
| `qt-cluster` | the monorepo (`src/qt_cluster`), via `QNX_PROJECT_SRC` |
| `qtbase`, `qtdeclarative` | stock [meta-qt6](https://code.qt.io/yocto/meta-qt6.git) (6.10 branch), unmodified |
| `qnx-screen-virtio` | prebuilt `.apk` from repo.oss.qnx.com (`qnx-apk`) |

## Not done yet

1. **The cluster demo has not run end to end.** The guest launches under a host built
   from these layers and its paravirtual GPU initialises; `/scripts/start-guest1.sh` is
   the one-command path through producer, SOME/IP client, graphics and Qt, and has not
   been driven all the way through on hardware yet.
2. **The SOME/IP libraries still ride in the IFS.** `qt-cluster` and `qnx-screen-virtio`
   are on the rootfs; moving the rest is one line in `qnx-guest-rootfs`'s
   `QNX_ROOTFS_INSTALL` plus a template mapping.
3. **guest-2 and the Linux guest** from the original project have no recipes yet.
