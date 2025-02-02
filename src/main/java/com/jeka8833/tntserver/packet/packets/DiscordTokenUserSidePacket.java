package com.jeka8833.tntserver.packet.packets;

import com.jeka8833.tntserver.TNTServer;
import com.jeka8833.tntserver.database.PlayersDatabase;
import com.jeka8833.tntserver.database.RemoteDB;
import com.jeka8833.tntserver.database.storage.Bot;
import com.jeka8833.tntserver.database.storage.Player;
import com.jeka8833.tntserver.database.storage.User;
import com.jeka8833.tntserver.packet.Packet;
import com.jeka8833.tntserver.packet.PacketInputStream;
import com.jeka8833.tntserver.packet.PacketOutputStream;
import com.jeka8833.tntserver.packet.packets.webendpoints.DiscordTokenEndpointSidePacket;
import org.java_websocket.WebSocket;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;

public class DiscordTokenUserSidePacket implements Packet {
    public static final int INTERNAL_ERROR = 0;
    public static final int CONNECTION_ERROR = 1;
    public static final int MESSAGE_GOOD_LINK = 2;
    public static final int MESSAGE_BAD_LINK = 3;
    public static final int MESSAGE_GOOD_UNLINK = 4;
    public static final int MESSAGE_BAD_UNLINK = 5;
    public static final int TRY_LINK = 6;
    public static final int TRY_UNLINK = 7;

    private UUID user;
    private int code;
    private int statusCode;

    @SuppressWarnings("unused")
    public DiscordTokenUserSidePacket() {
        this(null, Integer.MIN_VALUE, INTERNAL_ERROR);
    }

    public DiscordTokenUserSidePacket(UUID user, int code, int statusCode) {
        this.user = user;
        this.code = code;
        this.statusCode = statusCode;
    }

    @Override
    public void write(PacketOutputStream stream) throws IOException {
        stream.writeUUID(user);
        stream.writeInt(code);
        stream.writeInt(statusCode);
    }

    @Override
    public void read(PacketInputStream stream) throws IOException {
        user = stream.readUUID();
        code = stream.readInt();
        statusCode = stream.readInt();
    }

    @Override
    public void serverProcess(WebSocket socket, @Nullable User user) {
        if (user instanceof Player player) {
            Bot serverTokenizer = PlayersDatabase.getBotWithPrivilege("SERVER_DISCORD_LINK");
            if (serverTokenizer == null) {
                TNTServer.serverSend(socket, new DiscordTokenUserSidePacket(player.uuid, code, CONNECTION_ERROR));
                return;
            }

            WebSocket serverTokenizerSocket = serverTokenizer.getSocket();
            if (serverTokenizerSocket == null) {
                TNTServer.serverSend(socket, new DiscordTokenUserSidePacket(player.uuid, code, CONNECTION_ERROR));
                return;
            }

            if (statusCode == TRY_LINK) {
                TNTServer.serverSend(serverTokenizerSocket, new DiscordTokenEndpointSidePacket(player.uuid, code));
            } else if (statusCode == TRY_UNLINK) {
                RemoteDB.removeDiscordUser(player.uuid);
                TNTServer.serverSend(socket,
                        new DiscordTokenUserSidePacket(player.uuid, code, MESSAGE_GOOD_UNLINK));
            } else {
                TNTServer.serverSend(socket, new DiscordTokenUserSidePacket(player.uuid, code, INTERNAL_ERROR));
            }
        } else {
            socket.close();
        }
    }
}
