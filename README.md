Java Implementation of Minecraft's Rcon Protocol
=============

Originally adapted from the PHP implementation featured [here](https://gist.github.com/1292348/04dc5fa6bb0f34b4d600a4228299f9386bda50d3 "Gist").

This implementation listens for HTTP GET requests on localhost:8181 and passes the command query onto the rcon connection, which is kept alive. A ping command is sent regularly to ensure that the connection is still active, although whether this is strictly necessary is debatable.
