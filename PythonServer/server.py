import ssl
import rsa
import json
import socket
import base64
import sqlite3
import threading

# Active connections hashmap (phone_hash:socket_connection)
connections = {}

# SQL database setup
conn = sqlite3.connect("server_accounts.db", check_same_thread=False)
cursor = conn.cursor()
cursor.execute("""
CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone_hash TEXT UNIQUE NOT NULL,
    public_key BINARY NOT NULL,
    key_sig BINARY NOT NULL,
    messages TEXT DEFAULT "{}"
)
""")

def try_add_message(sender_phone_hash, receiver_phone_hash, message):
    # Tries to add a message to an account in the database
    cursor.execute("SELECT messages FROM accounts WHERE phone_hash = ?", (receiver_phone_hash,))
    result = cursor.fetchone()
    if result:
        messages = json.loads(result[0])
        if sender_phone_hash not in messages:
            messages[sender_phone_hash] = []
        messages[sender_phone_hash].append(message)
        cursor.execute("UPDATE accounts SET messages = ? WHERE phone_hash = ?", (json.dumps(messages), receiver_phone_hash))
        conn.commit()

def remove_messages(phone_hash):
    # Removes all messages from an account in the database
    cursor.execute("SELECT messages FROM accounts WHERE phone_hash = ?", (phone_hash,))
    result = cursor.fetchone()
    if result:
        messages = json.loads(result[0])
        if len(messages) > 0:
            cursor.execute("UPDATE accounts SET messages = ? WHERE phone_hash = ?", ("{}", phone_hash))
            conn.commit()
            return messages
    return {}

def account_exists(phone_hash):
    # Search SQL database for a specific hash
    cursor.execute("SELECT 1 FROM accounts WHERE phone_hash = ?", (phone_hash,))
    return cursor.fetchone() is not None

def handle_client(connection, address):
    print(f"Connection from {address} has been established.")
    conn_phone_hash = "" # String of the connections phone number hash
    try:
        while True:
            # Receive data from the client
            try:
                data = connection.recv(1024)
            except ConnectionResetError:
                break
            if not data:
                break
            if len(data) < 32 or len(data) > 1024:
                break
            if data[0] == 64 and len(data) == 288 and conn_phone_hash == "": # Existing account is online
                tmp_phone_hash = data[1:32].decode('ascii')
                cursor.execute("SELECT public_key FROM accounts WHERE phone_hash = ?", (tmp_phone_hash,))
                result = cursor.fetchone()
                if result:
                    try:
                        public_key = rsa.PublicKey.load_pkcs1(result[0], format="DER")
                        rsa.verify(data[1:32], data[32:], public_key)
                    except rsa.VerificationError:
                        continue
                    conn_phone_hash = tmp_phone_hash
                    connections[conn_phone_hash] = connection
                    messages = remove_messages(conn_phone_hash)
                    for sender in messages.keys():
                        for m in messages[sender]:
                            connection.sendall(bytes([65])+sender.encode("ascii")+base64.b64decode(m))
            elif data[0] == 65 and len(data) > 288 and conn_phone_hash == "": # Create new account if possible and set online
                tmp_phone_hash = data[1:32].decode('ascii')
                if not account_exists(tmp_phone_hash):
                    conn_phone_hash = tmp_phone_hash
                    connections[conn_phone_hash] = connection
                    cursor.execute("INSERT INTO accounts (phone_hash, public_key, key_sig) VALUES (?, ?, ?)", (conn_phone_hash, data[288:], data[32:288]))
                    conn.commit()
                    connection.sendall(bytes([64, 1]))
                else:
                    connection.sendall(bytes([64, 0]))
            elif data[0] == 66 and len(data) == 32 and conn_phone_hash != "": # Get public key and sig from phone hash if possible
                phone_hash = data[1:32].decode('ascii')
                cursor.execute("SELECT key_sig, public_key FROM accounts WHERE phone_hash = ?", (phone_hash,))
                result = cursor.fetchone()
                if result:
                    connection.sendall(bytes([64])+result[0]+result[1])
                else:
                    connection.sendall(bytes([64, 0]))
            elif data[0] == 67 and len(data) > 32 and conn_phone_hash != "": # Send message to someone
                phone_hash = data[1:32].decode('ascii')
                if phone_hash in connections: # Send directly
                    connections[phone_hash].sendall(bytes([65])+conn_phone_hash.encode("ascii")+data[32:])
                else: # Add to database
                    try_add_message(conn_phone_hash, phone_hash, base64.b64encode(data[32:]).decode("ascii"))
    finally:
        # Cleanup
        if conn_phone_hash in connections:
            del connections[conn_phone_hash]
        connection.close()
        print(f"Connection from {address} has been closed.")

def start_server(host="127.0.0.1", port=7788, max_clients=8):
    # Create a TCP SSL socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
    context.load_cert_chain(certfile="cert.pem", keyfile="key.pem")
    # Bind the socket and listen
    server_socket.bind((host, port))
    server_socket.listen(max_clients)
    print(f"Server started on {host}:{port}")
    # Create new thread "handle_client" for each connection
    try:
        while True:
            client_connection, client_address = server_socket.accept()
            secure_connection = context.wrap_socket(client_connection, server_side=True)
            client_thread = threading.Thread(target=handle_client, args=(secure_connection, client_address), daemon=True)
            client_thread.start()
    finally:
        # Cleanup
        server_socket.close()
        conn.close()

if __name__ == "__main__":
    start_server()