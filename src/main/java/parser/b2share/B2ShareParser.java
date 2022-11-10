package parser.b2share;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eosc.eu.ParsersConfig.ParserConfig;
import eosc.eu.ParserService;
import eosc.eu.TransferServiceException;
import eosc.eu.model.*;
import parser.ParserHelper;


/***
 * Class for parsing B2Share DOIs
 */
public class B2ShareParser implements ParserService {

    private static final Logger LOG = Logger.getLogger(B2ShareParser.class);

    private String id;
    private String name;
    private URL urlServer;
    private String recordId;
    private int timeout;
    private B2Share parser;


    /***
     * Constructor
     */
    public B2ShareParser(String id) { this.id = id; }

    /***
     * Initialize the REST client for B2Share
     * @return true on success
     */
    public boolean init(ParserConfig serviceConfig) {

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        if (null != this.parser)
            return true;

        if(null == this.urlServer) {
            LOG.error("Missing B2Share server, call canParseDOI() first");
            return false;
        }

        LOG.debugf("Obtaining REST client for B2Share server %s", this.urlServer);

        try {
            // Create the REST client for the parser service
            this.parser = RestClientBuilder.newBuilder()
                            .baseUrl(this.urlServer)
                            .build(B2Share.class);

            return true;
        }
        catch (RestClientDefinitionException e) {
            LOG.error(e.getMessage());
        }

        return false;
    }

    /***
     * Get the Id of the parser.
     * @return Id of the parser service.
     */
    public String getId() { return this.id; }

    /***
     * Get the human-readable name of the parser.
     * @return Name of the parser service.
     */
    public String getName() { return this.name; }

    /***
     * Get the Id of the source record.
     * @return Source record Id.
     */
    public String sourceId() { return this.recordId; }

    /***
     * Checks if the parser service understands this DOI.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return Return true if the parser service can parse this DOI.
     */
    public Uni<Tuple2<Boolean, ParserService>> canParseDOI(String auth, String doi, ParserHelper helper) {
        boolean isValid = null != doi && !doi.isBlank();
        if(!isValid)
            return Uni.createFrom().item(Tuple2.of(false, this));

        // Check if DOI redirects to a B2Share record
        var result = Uni.createFrom().item(helper.getRedirectedToUrl())

            .chain(redirectedToUrl -> {
                if(null != redirectedToUrl)
                    return Uni.createFrom().item(redirectedToUrl);

                return helper.checkRedirect(doi);
            })
            .chain(redirectedToUrl -> {
                boolean redirectValid = (null != redirectedToUrl) && !doi.equals(redirectedToUrl);
                if(redirectValid) {
                    // Redirected, validate redirection URL
                    Pattern p = Pattern.compile("^(https?://[^/:]*b2share[^/:]*:?[\\d]*)/records/(.+)", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(redirectedToUrl);
                    redirectValid = m.matches();

                    if(redirectValid) {
                        this.recordId = m.group(2);
                        try {
                            this.urlServer = new URL(m.group(1));
                        } catch (MalformedURLException e) {
                            LOG.error(e.getMessage());
                            redirectValid = false;
                        }
                    }
                }

                return Uni.createFrom().item(Tuple2.of(redirectValid, (ParserService)this));
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Failed to check if DOI %s points to B2Share record", doi);
            });

        return result;
    }

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return List of files in the data set.
     */
    public Uni<StorageContent> parseDOI(String auth, String doi) {
        if(null == this.parser)
            return Uni.createFrom().failure(new TransferServiceException("invalidConfig"));

        if(null == this.recordId || this.recordId.isEmpty())
            return Uni.createFrom().failure(new TransferServiceException("noRecordId"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("doiParseTimeout"))
            .chain(unused -> {
                // Get B2Share record details
                return this.parser.getRecordAsync(this.recordId);
            })
            .chain(record -> {
                // Got B2Share record
                LOG.infof("Got B2Share record %s", record.id);

                // Get bucket that holds the files
                String linkToFiles = (null != record.links) ? record.links.get("files") : null;
                if(null != linkToFiles) {
                    Pattern p = Pattern.compile("^https?://[^/:]+/api/files/(.+)", Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(linkToFiles);
                    if(m.matches()) {
                        // Get files in the bucket
                        var bucket = m.group(1);
                        return this.parser.getFilesInBucketAsync(bucket);
                    }
                }

                return Uni.createFrom().failure(new TransferServiceException("noFilesLink"));
            })
            .chain(bucket -> {
                // Build list of source files
                StorageContent srcFiles = new StorageContent(bucket.contents.size());
                for(var file : bucket.contents) {
                    srcFiles.elements.add(new StorageElement(file));
                }

                srcFiles.count = srcFiles.elements.size();

                // Success
                return Uni.createFrom().item(srcFiles);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

}