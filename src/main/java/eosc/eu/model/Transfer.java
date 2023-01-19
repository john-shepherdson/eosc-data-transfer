package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;


/**
 * A new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transfer {

    private static final Logger LOG = Logger.getLogger(Transfer.class);

    private String badUrl;

    public List<TransferPayload> files;
    public TransferParameters params;


    /**
     * Constructor
     */
    public Transfer() { this.files = new ArrayList<>(); }

    public String getInvalidUrl() { return this.badUrl; }

    /***
     * Get all destination systems (from all payloads) in this transfer.
     * @param protocol Only return hostnames from URLs that match this protocol, pass null or empty to match all.
     * @return List of destination hostnames, null on error
     */
    public List<String> allDestinationStorages(String protocol) {
        var hosts = new HashSet<String>();
        var destinations = new ArrayList<String>();

        for(var payload : this.files){
            for(var destUrl : payload.destinations) {
                // Parse the destination URL and get the hostname
                try {
                    URI uri = new URI(destUrl);
                    String proto = uri.getScheme();
                    if(proto.equalsIgnoreCase(Destination.s3.toString()))
                        this.params.setS3Destinations(true);

                    if(null != protocol && !proto.equalsIgnoreCase(protocol))
                        // Destination URL does not fit the protocol we want, skip it
                        continue;

                    String host = uri.getHost().toLowerCase();
                    if(!hosts.contains(host)) {
                        // This is a new distinct hostname
                        hosts.add(host);
                        destinations.add(host);
                    }
                }
                catch (URISyntaxException e) {
                    LOG.error(e);
                    LOG.errorf("Invalid destination URL %s", destUrl);
                    this.badUrl = destUrl;
                    return null;
                }
            }
        }

        return destinations;
    }


    /***
     * The supported transfer destinations
     * Used as list of values for "dest" parameter of the API endpoints
     */
    public enum Destination
    {
        dcache("dcache"),
        s3("s3"),
        ftp("ftp");

        // TODO: Keep in sync with supported destinations in configuration file

        private String destination;

        Destination(String destination) { this.destination = destination; }

        public String getDestination() { return this.destination; }
    }
}
