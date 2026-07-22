# meta-qnx-guest

Images for QNX guests running under the hypervisor host. Policy only — every
mechanism comes from [meta-qnx](../meta-qnx), and the shared application recipes
come from [meta-qnx-hyp](../meta-qnx-hyp).

## What it builds

```bash
bitbake qnx-guest-image     # a bootable guest IFS
bitbake spi-loopback        # SPI loopback test
```

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
`frame-router`). Duplicating those recipes per image layer would be worse than
depending on the layer that holds them. If the application recipes ever outgrow
that arrangement they should move to a layer of their own, with both image
layers depending on it.

## Application sources

Each recipe clones its own repository and tracks the branch head — see
[meta-qnx/docs/variables.md](../meta-qnx/docs/variables.md) for `QNX_SRC_*`.

| Recipe | Source |
| --- | --- |
| `spi-loopback` | `PM-Maestro-ITI-GP-Org/spi_loopback` |
| `motor-ai-client` | `PM-Maestro-ITI-GP-Org/motor_ai_client` — **blocked**, see below |

## Not done yet

1. **`motor-ai-client` cannot build.** It sources
   `${SOMEIP_DIR}/commonapi-qnx/scripts/env.sh` for the generated CommonAPI
   bindings, the vsomeip libraries and the code generators. `someip` is ~1.5 GB,
   still lives in the monorepo, and has no repository, so there is nothing to
   `DEPENDS` on. The recipe exists, records the URL, and skips with an
   explanation rather than failing a guest build.
2. **The Qt cluster.** `qt_cluster` is the application (small, CMake, has its own
   repo); `src/QT` is a 34 GB prebuilt Qt6-for-QNX tree that wants staging rather
   than compiling — and belongs on a data partition, not in a RAM-resident IFS.
3. **No data partition for guests.** The real guest-1 carries a `rootfs.img`
   holding the Qt deploy tree and the someip libraries, for exactly that reason.
4. **No `.qvmconf`.** The host cannot launch this guest yet; nothing generates
   the vdev configuration qvm reads.
5. **`fb_host` ends up in the guest.** `frame-router` builds and stages all three
   binaries, and the automatic entry pass installs whatever a recipe staged. It
   is ~24 KB of dead weight in a guest; splitting the recipe or adding per-image
   file selection would fix it.
