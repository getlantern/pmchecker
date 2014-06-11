package org.lantern.pmchecker;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import fr.free.miniupnp.IGDdatas;
import fr.free.miniupnp.MiniupnpcLibrary;
import fr.free.miniupnp.UPNPDev;
import fr.free.miniupnp.UPNPUrls;
import fr.free.miniupnp.libnatpmp.NatPmp;
import fr.free.miniupnp.libnatpmp.NatPmpResponse;

public class PMChecker {
    private static final int UPNP_DELAY = 2000;

    public static void main(String[] args) {
        System.out.println("Checking whether or not we can port map with UPnP or NAT-PMP. This can take a while ...");
        int[] externalPorts = new int[] {
                8443, 443
        };
        int startingLocalPort = 15600;

        SortedMap<Integer, Boolean> natPMPResults = new TreeMap<Integer, Boolean>();
        SortedMap<Integer, Boolean> upnpResults = new TreeMap<Integer, Boolean>();

        boolean isNatPMPSupported = isNatPMPSupported();

        // UPnP
        for (int externalPort : externalPorts) {
            // Delete mappings
            if (isNatPMPSupported) {
                natPMPMap(startingLocalPort, externalPort, 0);
            }
            removeUPnPMapping(externalPort);
            upnpResults.put(externalPort,
                    canUPnPMap(startingLocalPort++, externalPort));
        }

        // NatPMP
        for (int externalPort : externalPorts) {
            // Delete mappings
            if (isNatPMPSupported) {
                natPMPMap(startingLocalPort, externalPort, 0);
            }
            removeUPnPMapping(externalPort);
            natPMPResults.put(externalPort,
                    isNatPMPSupported && natPMPMap(startingLocalPort++, externalPort, 1));
        }

        for (Map.Entry<Integer, Boolean> entry : upnpResults.entrySet()) {
            System.out.println(String.format("Can map %s on UPnP? %s",
                    entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<Integer, Boolean> entry : natPMPResults.entrySet()) {
            System.out.println(String.format("Can map %s on NAT-PMP? %s",
                    entry.getKey(), entry.getValue()));
        }
    }

    private static boolean isNatPMPSupported() {
        NatPmp igd = new NatPmp();
        igd.sendPublicAddressRequest();
        for (int i = 0; i < 5; ++i) {
            NatPmpResponse response = new NatPmpResponse();
            int result = igd.readNatPmpResponseOrRetry(response);
            if (result == 0) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // fallthrough
            }
        }
        return false;
    }

    private static boolean natPMPMap(int localPort, int externalPort,
            int timeout) {
        NatPmpResponse response = new NatPmpResponse();
        int TCP = 2; // TCP
        int result = -1;

        NatPmp igd = new NatPmp();
        igd.sendNewPortMappingRequest(TCP, localPort, externalPort,
                timeout);
        for (int i = 0; i < 80; ++i) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // fallthrough
            }
            result = igd.readNatPmpResponseOrRetry(response);
            if (result == 0) {
                break;
            }
        }

        return result == 0;
    }

    private static boolean canUPnPMap(int localPort, int externalPort) {
        removeUPnPMapping(externalPort);

        ByteBuffer lanaddr = ByteBuffer.allocate(16);
        ByteBuffer intClient = ByteBuffer.allocate(16);
        ByteBuffer intPort = ByteBuffer.allocate(6);
        ByteBuffer desc = ByteBuffer.allocate(80);
        ByteBuffer enabled = ByteBuffer.allocate(4);
        ByteBuffer leaseDuration = ByteBuffer.allocate(16);
        int ret;

        final UPNPUrls urls = new UPNPUrls();
        final IGDdatas data = new IGDdatas();

        UPNPDev devlist = MiniupnpcLibrary.INSTANCE.upnpDiscover(UPNP_DELAY,
                (String) null,
                (String) null, 0, 0, IntBuffer.allocate(1));
        if (devlist == null) {
            return false;
        }
        ret = MiniupnpcLibrary.INSTANCE.UPNP_GetValidIGD(devlist, urls, data,
                lanaddr, 16);
        if (ret == 0) {
            MiniupnpcLibrary.INSTANCE.FreeUPNPUrls(urls);
            devlist.setAutoRead(false);
            MiniupnpcLibrary.INSTANCE.freeUPNPDevlist(devlist);
            return false;
        }

        try {
            ByteBuffer externalAddress = ByteBuffer.allocate(16);
            MiniupnpcLibrary.INSTANCE.UPNP_GetExternalIPAddress(
                    urls.controlURL.getString(0),
                    zeroTerminatedString(data.first.servicetype),
                    externalAddress);
            String publicIp = zeroTerminatedString(externalAddress.array());

            ret = MiniupnpcLibrary.INSTANCE.UPNP_AddPortMapping(
                    urls.controlURL.getString(0), // controlURL
                    zeroTerminatedString(data.first.servicetype), // servicetype
                    "" + externalPort, // external Port
                    "" + localPort, // internal Port
                    zeroTerminatedString(lanaddr.array()), // internal client
                    "added via MiniupnpcLibrary.INSTANCE/JAVA !", // description
                    "TCP", // protocol UDP or TCP
                    null, // remote host (useless)
                    "0"); // leaseDuration

            return ret == MiniupnpcLibrary.UPNPCOMMAND_SUCCESS;
        } finally {
            MiniupnpcLibrary.INSTANCE.FreeUPNPUrls(urls);
            devlist.setAutoRead(false);
            MiniupnpcLibrary.INSTANCE.freeUPNPDevlist(devlist);
        }
    }

    private static boolean removeUPnPMapping(int externalPort) {
        UPNPDev devlist = MiniupnpcLibrary.INSTANCE.upnpDiscover(UPNP_DELAY,
                (String) null,
                (String) null, 0, 0, IntBuffer.allocate(1));
        if (devlist == null) {
            return false;
        }

        final UPNPUrls urls = new UPNPUrls();
        final IGDdatas data = new IGDdatas();

        ByteBuffer lanaddr = ByteBuffer.allocate(16);
        int ret = MiniupnpcLibrary.INSTANCE.UPNP_GetValidIGD(devlist, urls,
                data, lanaddr, 16);
        if (ret == 0) {
            devlist.setAutoRead(false);
            MiniupnpcLibrary.INSTANCE.freeUPNPDevlist(devlist);
            return false;
        }

        try {
            ret = MiniupnpcLibrary.INSTANCE.UPNP_DeletePortMapping(
                    urls.controlURL.getString(0),
                    zeroTerminatedString(data.first.servicetype), ""
                            + externalPort, "TCP", null);
            return ret == MiniupnpcLibrary.UPNPCOMMAND_SUCCESS;
        } finally {
            MiniupnpcLibrary.INSTANCE.FreeUPNPUrls(urls);
            devlist.setAutoRead(false);
            MiniupnpcLibrary.INSTANCE.freeUPNPDevlist(devlist);
        }
    }

    private static String zeroTerminatedString(byte[] array) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == 0) {
                return new String(array, 0, i, Charset.forName("UTF8"));
            }
        }
        return new String(array, Charset.forName("UTF8"));
    }
}
