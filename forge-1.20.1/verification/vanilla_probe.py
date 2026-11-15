"""Connect a minimal vanilla 1.20.1 client to the isolated loopback fixture."""
import socket
import struct
import time
import zlib


def var(value):
    output = bytearray()
    while value > 127:
        output.append((value & 127) | 128)
        value >>= 7
    output.append(value)
    return bytes(output)


def readvar(data, offset=0):
    value = 0
    for index in range(5):
        byte = data[offset + index]
        value |= (byte & 127) << (index * 7)
        if byte < 128:
            return value, offset + index + 1
    raise ValueError('bad varint')


def string(value):
    encoded = value.encode()
    return var(len(encoded)) + encoded


with socket.create_connection(('127.0.0.1', 25585), timeout=5) as sock:
    sock.settimeout(2)
    compression = False

    def send(packet_id, payload=b''):
        body = var(packet_id) + payload
        if compression:
            body = var(len(body)) + zlib.compress(body) if len(body) >= 256 else b'\0' + body
        sock.sendall(var(len(body)) + body)

    def exact(length):
        result = b''
        while len(result) < length:
            data = sock.recv(length - len(result))
            if not data:
                raise EOFError('server closed connection')
            result += data
        return result

    def receive():
        header = bytearray()
        while True:
            byte = exact(1)
            header += byte
            if byte[0] < 128:
                break
        size, _ = readvar(header)
        body = exact(size)
        if compression:
            length, offset = readvar(body)
            body = zlib.decompress(body[offset:]) if length else body[offset:]
        packet_id, offset = readvar(body)
        return packet_id, body[offset:]

    send(0, var(763) + string('127.0.0.1') + struct.pack('>H', 25585) + var(2))
    send(0, string('PlainVoxyTest') + b'\0')
    deadline = time.monotonic() + 20
    logged_in = False
    teleported = False
    while time.monotonic() < deadline and not teleported:
        try:
            packet_id, payload = receive()
        except socket.timeout:
            continue
        if not logged_in:
            if packet_id == 3:
                compression = True
            elif packet_id == 4:
                query, _ = readvar(payload)
                send(2, var(query) + b'\0')
            elif packet_id == 2:
                logged_in = True
            elif packet_id == 0:
                raise RuntimeError('Login rejected: ' + payload.decode(errors='replace'))
        elif packet_id == 35:
            send(18, payload)
        elif packet_id == 60:
            teleport_id, _ = readvar(payload, 33)
            send(0, var(teleport_id))
            teleported = True
    if not logged_in or not teleported:
        raise RuntimeError(f'Vanilla probe did not enter the world: login={logged_in}, teleport={teleported}')
    print('VANILLA_PROBE PASS')
