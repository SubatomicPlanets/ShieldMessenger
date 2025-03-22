# Shield
Shield is a simple end-to-end encrypted messenger.  
It was written as a learning project and is **NOT recommended to use!**  
The server is written in Python, and the client is an Android app written in Java.

## How it works
Shield does not use the signal protocol and thus is not as secure.  
Basic features like forward secrecy are not implemented.  
The only thing it does better than WhatsApp or Signal is that it hashes phone numbers using bcrypt.  
This means the server never sees any phone numbers, but users can still communicate using them.  
It also means that it can use the phone number as a shared secret to sign public keys.  
However, all this also means that the server cannot verify phone numbers.  
In the end, this was just me experimenting with cryptography.

## Running the server and client
- Run server.py to start the server.  
  If any modules are missing you can install them using pip.  
  To run on a different IP or port simply pass the parameters to start_server() on the last line.
- Before building and running the client make sure to change SERVER_ADDRESS in ShieldService.java.
- Also make sure the bcrypt salt is the same on the server and client (which it is by default).
- Lastly, for SSL to work you need to get a certificate (you can use OpenSSL to get a self-signed one).  
  For the server just add the cert.pem and key.pem files to the same directory as server.py.  
  For the client you can paste your self-signed certificate into the cert.pem file. If you have a certificate from a trusted CA instead, you can modify the SSL connection code and get rid of the cert.pem file.

## TODO
- Encrypt the SQL databases on both client and server.
- Somehow find a way to verify a phone number without the server knowing the actual number.
- Add a settings page in the app with a few settings like "Stay Anonymous" which doesn't send your phone number when texting someone.
- When messages are sent while offline, save them and only send them as soon as a connection is established.
- Display security codes for contacts.
- Maybe add message_id and last_message_id to encrypted messages so that the server can't duplicate or remove messages without clients noticing.
- When sending many texts after each other they all compute the hash until one of them caches it which is not very nice.
- Improve message storage so that there are no missed messages (for example, when a client loses connection but the server still thinks it's connected and sends a message directly instead of saving it to the database).
- Move contacts with new messages to the top and add an indicator like a blue dot.
- Maybe find a better way to pass data between activities and the service.
- Improve phone number hashing by using some kind of dynamic salt.
- Add more error checks and improve the code.
- Maybe use the Signal protocol.
