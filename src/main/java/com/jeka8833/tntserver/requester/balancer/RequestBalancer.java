package com.jeka8833.tntserver.requester.balancer;

import com.jeka8833.tntserver.database.PlayersDatabase;
import com.jeka8833.tntserver.requester.balancer.node.BalancerNode;
import com.jeka8833.tntserver.requester.storage.HypixelCompactStructure;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RequestBalancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestBalancer.class);
    private static final CopyOnWriteArrayList<BalancerNode> NODES = new CopyOnWriteArrayList<>();

    @NotNull
    public static HypixelCompactStructure get(@NotNull UUID requestedPlayer, @NotNull BalancerRefresh oldStorage)
            throws Exception {
        Collection<BalancerNode> usedNodes = new ArrayList<>(1);

        while (!Thread.interrupted()) {
            try {
                Collection<BalancerNode> availableNodes = getAvailableNodes(NODES, usedNodes);
                if (availableNodes.isEmpty()) break;

                Optional<BalancerNode> nodeOpt = getNode(requestedPlayer, oldStorage, availableNodes);
                if (nodeOpt.isEmpty()) break;

                BalancerNode node = nodeOpt.get();

                try {
                    usedNodes.add(node);

                    HypixelCompactStructure hypixelCompactStructure = node.get(requestedPlayer);

                    oldStorage.getRequestTimeMap().put(node.getNodeUUID(), System.nanoTime());

                    return hypixelCompactStructure;
                } finally {
                    node.releaseReserve();
                }
            } catch (SilentCancelException ignored) {
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                LOGGER.warn("Failed to load data from node", e);
            }
        }

        throw new SilentCancelException();
    }

    @NotNull
    private static Optional<BalancerNode> getNode(@NotNull UUID requestedPlayer,
                                                  @NotNull BalancerRefresh playerStorage,
                                                  @NotNull Collection<BalancerNode> nodes) {
        OptionalLong elapsedFromLastRequest = playerStorage.getElapsedFromLastRequest();
        if (elapsedFromLastRequest.isEmpty()) return getNode(nodes);

        boolean isTNTClientUser = PlayersDatabase.isUsesTNTClient(requestedPlayer);
        long delayBetweenRequests = playerStorage.getMinDelayBetweenRequests(isTNTClientUser, nodes);

        if (elapsedFromLastRequest.getAsLong() < delayBetweenRequests) return Optional.empty();

        return nodes.stream()
                .filter(requesterNode ->
                        playerStorage.canRequestAt(System.nanoTime(), isTNTClientUser, requesterNode) &&
                                requesterNode.canReserve())
                .findFirst();
    }

    @NotNull
    private static Optional<BalancerNode> getNode(@NotNull Collection<BalancerNode> nodes) {
        return nodes.stream()
                .filter(BalancerNode::canReserve)
                .findFirst();
    }

    @NotNull
    private static List<BalancerNode> getAvailableNodes(@NotNull Collection<BalancerNode> allNodes,
                                                        @NotNull Collection<BalancerNode> exclude) {
        return allNodes.stream()
                .filter(node -> !exclude.contains(node) && node.getAvailable() > 0)
                .sorted()
                .toList();
    }

    public static void addNode(@NotNull BalancerNode node) {
        NODES.addIfAbsent(node);
        NODES.sort(null);
    }

    public static void removeNode(@NotNull BalancerNode node) {
        NODES.remove(node);
        node.cancelAll();
    }

    public static BalancerNode[] getNodes() {
        return NODES.toArray(new BalancerNode[0]);
    }
}