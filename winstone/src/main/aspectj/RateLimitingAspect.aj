import net.winstone.core.WinstoneConstant;
import org.slf4j.LoggerFactory;

// Java EE servlet API
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

// Used to scope pointcuts
import net.winstone.core.listener.RequestHandlerThread;
import net.winstone.core.ServletConfiguration;
import net.winstone.core.SimpleRequestDispatcher;


// Rate Limiter
import edu.utwente.aop.tools.RateLimiter;
import java.util.concurrent.TimeUnit;

// exceptions from Servlet.service(..)
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

// We redirect the call flow to error servlet
import net.winstone.servlet.ErrorServlet;

/**
 * Ensure that the request is allowed by the RateLimiter before continuing..
 */
public privileged aspect RateLimitingAspect  percflow(processRequest()) {
    private enum State { NOTICKET, TICKET, NOTICKET_AVAILABLE };

    public final int RESPONSE_TIME_MS = 1000;

    /** public because it is implicitly accessed from WebAppConfiguration */
    public State state = State.NOTICKET;

    static org.slf4j.Logger logger = LoggerFactory.getLogger(Servlet.class);
    // Allow 5 requests per limit
    private static RateLimiter limits = new RateLimiter(5, 1, TimeUnit.MINUTES);

    /**
     * Pointcut that captures all requests flows in which a request is handled.
     * This is used to instantiate this aspect per request that is handled.
     *
     * within(RequestHandlerThread) is not needed because processRequest is private, and the compositional
     * behaviour that would result from calling this using a priviliged aspect probably
     * would be sensible.
     */
    pointcut processRequest(): call(void RequestHandlerThread.processRequest(..));

    /**
     * Only service calls from within the configuration should be augmented
     */
    pointcut inWinstoneServletConfiguration(): within(ServletConfiguration);

    /**
     * When the control flow has reached HttpServletResponse.sendError we disable the ratelimiting
     */
    pointcut handlingError(): cflow(call(void HttpServletResponse.sendError(int, String)));

    /** Around did not work when trying to abstract call(...) && args into a seperate pointcut
     * @throws IOException when sendError throws an exception.
     */
    void around(ServletRequest req, ServletResponse resp) throws IOException:
            inWinstoneServletConfiguration() && !handlingError() &&
            call(void Servlet.service(..)) && args(req, resp) {
        if (!ensureTicket()) {
            // Instantiate new 503-UNAVAILABLE
            logger.info("No tickets: redirecting request to 503");
            ((HttpServletResponse) resp).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Rate limit reached - Throttled");
        } else {
            proceed(req, resp);
        }
    }

    /**
     * Try to get a ticket from the state machine
     * @return true when this aspect instance (control flow) has a valid ticket
     */
    private boolean ensureTicket() {
        switch (state) {
            case TICKET:
                logger.info("Re-using ticket");
                break;
            case NOTICKET:
                if (limits.acquire(RESPONSE_TIME_MS)) {
                    logger.info("Acquired new ticket for {}", this);
                    state = State.TICKET;
                } else {
                    state = State.NOTICKET_AVAILABLE;
                }
                break;
            case NOTICKET_AVAILABLE:
                logger.info("No tickets available");
                break;
        }

        return state == State.TICKET;
    }
}
