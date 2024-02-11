package com.craftmend.openaudiomc.generic.api.implementaions;

import com.craftmend.openaudiomc.OpenAudioMc;
import com.craftmend.openaudiomc.api.VoiceApi;
import com.craftmend.openaudiomc.api.clients.Client;
import com.craftmend.openaudiomc.api.voice.CustomPlayerFilter;
import com.craftmend.openaudiomc.api.voice.VoicePeerOptions;
import com.craftmend.openaudiomc.generic.client.objects.ClientConnection;
import com.craftmend.openaudiomc.generic.networking.interfaces.NetworkingService;
import com.craftmend.openaudiomc.generic.networking.packets.client.voice.PacketClientVoiceOptionsUpdate;
import com.craftmend.openaudiomc.generic.networking.payloads.client.voice.ClientVoiceOptionsPayload;
import com.craftmend.openaudiomc.generic.platform.Platform;
import com.craftmend.openaudiomc.spigot.modules.voicechat.filters.FilterService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class VoiceApiImpl implements VoiceApi {

    @Override
    public boolean hasPeer(Client haystack, Client needle) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        return hasPeer(haystack, needle.getActor().getUniqueId());
    }

    @Override
    public boolean hasPeer(Client haystack, UUID needle) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        ClientConnection clientConnection = (ClientConnection) haystack;
        return clientConnection.getRtcSessionManager().isPeer(needle);
    }

    @Override
    public void updatePeerOptions(Client client, Client peerToUpdate, VoicePeerOptions options) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        Objects.requireNonNull(peerToUpdate, "Peer cannot be null");
        Objects.requireNonNull(options, "Options cannot be null");

        ClientConnection clientConnection = (ClientConnection) client;
        ClientConnection peerConnection = (ClientConnection) peerToUpdate;

        // do we have this peer?
        if (!clientConnection.getRtcSessionManager().isPeer(peerConnection.getActor().getUniqueId())) {
            throw new IllegalArgumentException("Peer is not connected to this client");
        }

        // update the options
        ClientConnection peerCon = OpenAudioMc.getService(NetworkingService.class).getClient(peerConnection.getUser().getUniqueId());
        PacketClientVoiceOptionsUpdate packet = new PacketClientVoiceOptionsUpdate(
                new ClientVoiceOptionsPayload(peerCon.getRtcSessionManager().getStreamKey(), options)
        );
        clientConnection.sendPacket(packet);
    }

    private boolean isProximityPeer(Client haystack, Client needle) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        ClientConnection haystackConnection = (ClientConnection) haystack;
        return haystackConnection.getRtcSessionManager().getCurrentProximityPeers().contains(needle.getActor().getUniqueId());
    }

    public boolean isGlobalPeer(Client haystack, Client needle) {
        ClientConnection haystackConnection = (ClientConnection) haystack;
        return haystackConnection.getRtcSessionManager().getCurrentGlobalPeers().contains(needle.getActor().getUniqueId());
    }

    @Override
    public void addStaticPeer(Client client, Client peerToAdd, boolean visible, boolean mutual) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        VoicePeerOptions options = new VoicePeerOptions();
        options.setSpatialAudio(false);
        options.setVisible(visible);

        ClientConnection clientConnection = (ClientConnection) client;
        ClientConnection peerConnection = (ClientConnection) peerToAdd;

        if (!clientConnection.getRtcSessionManager().isReady() || !peerConnection.getRtcSessionManager().isReady()) {
            throw new IllegalStateException("Both clients must be ready (connected and have voice chat enabled) before adding a peer");
        }

        if (isProximityPeer(client, peerToAdd)) {
            updatePeerOptions(client, peerToAdd, options);
            clientConnection.getRtcSessionManager().getCurrentGlobalPeers().add(peerToAdd.getActor().getUniqueId());
            clientConnection.getRtcSessionManager().getCurrentProximityPeers().remove(peerToAdd.getActor().getUniqueId());
        } else {
            clientConnection.getRtcSessionManager().getCurrentGlobalPeers().add(peerToAdd.getActor().getUniqueId());
            clientConnection.getPeerQueue().addSubscribe(peerConnection, clientConnection, options);
        }

        if (mutual) {
            addStaticPeer(peerToAdd, client, visible, false);
        }
    }

    @Override
    public void removeStaticPeer(Client client, Client peerToRemove, boolean mutual) {
        if (OpenAudioMc.getInstance().getPlatform() != Platform.SPIGOT) {
            throw new IllegalStateException("This method is only available on the spigot platform");
        }

        if (isGlobalPeer(client, peerToRemove)) {
            ClientConnection clientConnection = (ClientConnection) client;
            ClientConnection peerConnection = (ClientConnection) peerToRemove;
            clientConnection.getRtcSessionManager().getCurrentGlobalPeers().remove(peerToRemove.getActor().getUniqueId());
            clientConnection.getPeerQueue().drop(peerConnection.getRtcSessionManager().getStreamKey());
        }

        if (mutual) {
            removeStaticPeer(peerToRemove, client, false);
        }
    }

    @Override
    public void addFilterFunction(CustomPlayerFilter customPlayerFilter) {
        OpenAudioMc.getService(FilterService.class).addCustomFilter(customPlayerFilter);
    }

    @Override
    public List<CustomPlayerFilter> getCustomPlayerFilters() {
        return OpenAudioMc.getService(FilterService.class).getCustomPlayerFilters();
    }
}
