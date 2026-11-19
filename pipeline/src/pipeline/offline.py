"""
PIPELINE_OFFLINE=1: no API client and no network connection from this process.

Every place that creates an Anthropic client goes through anthropic_client(); every other outgoing call
(downloads, live-manifest fetches, Firebase deploys) calls require_online() first. block_sockets() also makes
any socket connection raise, so a code path that was missed fails loudly instead of reaching the network.
Unix-domain sockets (local IPC) stay allowed.
"""
from __future__ import annotations

import os
import socket

ENV = "PIPELINE_OFFLINE"


class OfflineError(RuntimeError):
    pass


def offline() -> bool:
    return os.environ.get(ENV, "").strip().lower() not in ("", "0", "false", "no")


def require_online(what: str) -> None:
    if offline():
        raise OfflineError(f"{ENV}=1: refusing to {what}. Unset {ENV} to allow API or network calls.")


def anthropic_client():
    """A Messages API client, or OfflineError when PIPELINE_OFFLINE is set (checked before the SDK is touched)."""
    require_online("create an Anthropic API client")
    import anthropic
    return anthropic.Anthropic()


_real_connect = socket.socket.connect
_real_connect_ex = socket.socket.connect_ex
_real_create_connection = socket.create_connection


def _local(sock) -> bool:
    return getattr(socket, "AF_UNIX", None) is not None and sock.family == socket.AF_UNIX


def _connect(self, address):
    if not _local(self):
        raise OfflineError(f"{ENV}=1: network connection to {address!r} blocked")
    return _real_connect(self, address)


def _connect_ex(self, address):
    if not _local(self):
        raise OfflineError(f"{ENV}=1: network connection to {address!r} blocked")
    return _real_connect_ex(self, address)


def _create_connection(address, *args, **kwargs):
    raise OfflineError(f"{ENV}=1: network connection to {address!r} blocked")


def block_sockets() -> None:
    socket.socket.connect = _connect
    socket.socket.connect_ex = _connect_ex
    socket.create_connection = _create_connection


def unblock_sockets() -> None:
    socket.socket.connect = _real_connect
    socket.socket.connect_ex = _real_connect_ex
    socket.create_connection = _real_create_connection


if offline():
    block_sockets()
