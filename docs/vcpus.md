# Two vCPUs: why the second one needs a higher priority than the first

Adding a second `cpu` line to the guest's `.qvmconf` is not enough. Done plainly
the guest either takes a very long time to boot or comes up with no network at
all:

```
Starting networking ...
ifconfig: interface vtnet0 does not exist
route: writing to routing socket: Network is unreachable
```

The fix is one word on one line, and it is asymmetric:

```
cpu
cpu sched 20r
```

The **boot CPU keeps qvm's own priority. Every AP after it runs higher.** Not
both raised, not both lowered — the asymmetry is the fix.

## Why

`start_aps()` in the QNX startup brings each secondary CPU up like this:

```c
count = 0;
do {
    if(++count == 0) ap_fail(i);
} while(cpu_starting != 0);
```

The boot CPU spins on a plain memory location until the AP it has just powered
on clears the flag. Nothing in that loop traps.

As a guest vCPU that is the worst possible shape. The hypervisor never sees an
exit, so it cannot preempt the spinning vCPU, and on a busy four-core host the
AP's own thread may wait a long time for a core. The boot CPU burns a core doing
nothing, waiting for a thread it is itself starving. Whoever is being waited for
has to outrank whoever is waiting, or the wait is the reason the wait never ends.

Twenty is below io-sock's 21, so a guest still cannot starve host networking.
Round-robin, so the AP timeslices rather than holding a core outright.

## Getting it backwards

Worth stating plainly, because the reasoning that leads there is superficially
sound. It is tempting to argue that a vCPU should sit *below* qvm's own threads —
qvm's vdev threads service the virtio rings, so surely the backend should
outrank the guest it serves, or the guest starves its own I/O.

That was tried here as `cpu sched 9r` on both vCPUs. The result was the log at
the top of this page. Device probe is the same shape of handshake as the AP
start: the guest driver drives it, so it needs the vCPU scheduled. Starve the
vCPU and the probe does not slow down, it fails — `devs-vtnet_mmio` never
attaches, `vtnet0` never exists, and the guest spends the rest of its life with
no network. The backend was never the bottleneck; the guest was.

## The rest of the reference commit

[`0c1ce7f`](https://github.com/PM-Maestro-ITI-GP-Org/Qnx_Hypervisor_rbye/commit/0c1ce7fe1153bf00e8916f56f177f1d318228fa6)
carries four changes. Two are config and this tree takes both:

| | | here |
| --- | --- | --- |
| `cpu sched 20r` on the AP | the fix above | **taken** |
| `fdt generate` | the guest reads its CPU and GIC topology from qvm's FDT instead of probing | **taken** |
| `-P2` on a locally built `startup-armv8_fm` | tells startup how many CPUs to expect | not needed — see below |
| a trapped MMIO read inside `start_aps()` | forces the spin to exit to the hypervisor | not needed — see below |

### `-P2` and the patched startup

The reference points its build file at a startup built from the BSP source tree
rather than the SDP's, so it can pass `-P2` and carry the patch below. This tree
boots the stock `startup-armv8_fm -H` and does not pass `-P`, so the CPU count
has to come from somewhere else — which is what `fdt generate` is for. No patch
to carry across SDP updates.

### The MMIO poke

The patch adds, inside the spin:

```c
if ((count & 0xFF) == 0) (void)in32(gicd_paddr_base + 0x8);
```

A read of a GIC distributor register every 256 iterations. The value is
discarded; the access itself is the point, because it traps and hands qvm a
chance to schedule the AP. It is a real fix for the real defect — the loop
cannot yield — and it costs un-`static`ing `gicd_paddr_base` in `gic_v3.c`, a
hardcoded register offset, and a permanently patched startup.

Priority alone has been enough here so far. If it ever is not, the better patch
is `wfe` rather than an MMIO read: it is the instruction the architecture
provides for exactly this, it traps to the hypervisor when `HCR_EL2.TWE` is set,
and it needs no global and no magic offset.

## The knobs

In `meta-qnx-guest/conf/qnx-guest-vdevs.inc`:

| | default | |
| --- | --- | --- |
| `QNX_GUEST_VCPUS` | `2` | how many `cpu` lines |
| `QNX_GUEST_VCPU0_SCHED` | *(empty)* | boot CPU; empty means qvm's own priority |
| `QNX_GUEST_VCPU_AP_SCHED` | `20r` | every AP after the first |
| `QNX_GUEST_FDT` | `generate` | `suppress` to turn the FDT off |
| `QNX_GUEST_SSHD_PRIORITY` | `15` | what sshd runs at *inside* the guest |

That last one is a different namespace from the rest of this table: the others
are host priorities for the vCPU threads, this one is a priority inside the
guest. It is here because it answers the same question from the other side —
an ssh key exchange is CPU in the guest, and at the default 10 a handshake
queues behind the Qt cluster and the motor apps. 15 puts it above them and
below `spi-dwc` at 30, which must not be preempted by a login. See
[Priorities](../../meta-qnx-hyp/docs/applications.md#priorities) for the whole
ordering.

Set `QNX_GUEST_VCPUS = "1"` to go back to a uniprocessor guest — the AP priority
then has nothing to apply to, and none of this matters.

## If it goes wrong again

`.net-start.sh` in the guest now waits for the interface with `if_up -p` instead
of assuming it is there, and prints `ifconfig -l` and the stack's log when it is
not. So a vCPU-starvation failure says which interfaces *did* appear rather than
just naming the one that did not.

On the host, with the guest running:

```bash
pidin -P qvm -f narpS      # vCPU threads and their actual priorities
hogs -n -%1                # who is really consuming the four cores
```
