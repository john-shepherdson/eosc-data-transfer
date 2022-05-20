package eosc.eu;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;

import egi.fts.FileTransferService;
import parser.zenodo.Zenodo;


/**
 * The parameters of an action to perform.
 * Allows implementing actions as instances of Function<JobInfo, Response>.
 *
 */
public class ActionParameters {

    public Zenodo zenodo;
    public FileTransferService fts;
    public String source;
    public String destination;
    public String parseService;
    public String transferService;
    public String authorization;
    public Response response;


    /**
     * Constructor
     */
    public ActionParameters() {
        this.response = Response.ok().build();
    }

    /**
     * Construct from access token
     */
    public ActionParameters(String accessToken) {
        this.authorization = accessToken;
        this.response = Response.ok().build();
    }

    /**
     * Copy constructor
     */
    public ActionParameters(ActionParameters ap) {
        this.zenodo = ap.zenodo;
        this.fts = ap.fts;
        this.source = ap.source;
        this.destination = ap.destination;
        this.parseService = ap.parseService;
        this.transferService = ap.transferService;
        this.authorization = ap.authorization;
        this.response = ap.response;
    }

    /**
     * Check if the embedded API Response is a success
     */
    public boolean succeeded() {
        return Family.familyOf(this.response.getStatus()) == Family.SUCCESSFUL;
    }

    /**
     * Check if the embedded API Response is a failure
     */
    public boolean failed() {
        return Family.familyOf(this.response.getStatus()) != Family.SUCCESSFUL;
    }

    /**
     * Get failure Uni
     */
    public static <T> Uni<T> failedUni() {
        return Uni.createFrom().failure(new RuntimeException());
    }

    /**
     * Get failure Multi
     */
    public static <T> Multi<T> failedMulti() {
        return Multi.createFrom().failure(new RuntimeException());
    }
}
