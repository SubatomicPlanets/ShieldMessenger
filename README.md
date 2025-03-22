# Shield
Shield is a simple end-to-end encrypted messenger.  
It was written as a learning project and is NOT recommended to use!  
The server is written in Python, and the client is an Android app written in Java.

## How it works
Shield does not use the signal protocol and thus is not as secure.  
Basic features like forward secrecy are not implemented.  
The only thing it does better than WhatsApp or Signal is that it hashes phone numbers using bcrypt.  
This means the server never sees any phone numbers, but users can still communicate using them.  
However, this also means that the server cannot verify phone numbers.  
In the end, this was just me experimenting with cryptography.
