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
public aspect RateLimitingAspect  percflow(processRequest()) {
    private enum State { UNINITIALIZED, NOTICKET, TICKET, NOTICKET_AVAILABLE };

    public final int RESPONSE_TIME_MS = 1000;

    /** public because it is implicitly accessed from WebAppConfiguration */
    public State state = State.UNINITIALIZED;

    static org.slf4j.Logger logger = LoggerFactory.getLogger(Servlet.class);
    // Allow 5 requests per limit
    private static RateLimiter limits = new RateLimiter(5, 1, TimeUnit.MINUTES);

    pointcut processRequest(): call(* processRequest(..)) && within(RequestHandlerThread);

    pointcut servletCallService(Servlet servlet, ServletRequest req, ServletResponse resp): call(void Servlet.service(..)) && target(servlet) && args(req, resp);

    pointcut inWinstoneServletConfiguration(): within(ServletConfiguration);

    before(): processRequest() {
        logger.info("Processrequest!");
        state = State.NOTICKET;
    }

    // TODO: Rewrite to around and call service on different type.
    // TODO: Voor cflowbelow
    before(ServletRequest req, ServletResponse resp) throws ServletException, IOException: inWinstoneServletConfiguration() && call(void Servlet.service(..)) && args(req, resp) {
        logger.info(this.toString() + req + resp);

        getTicket();

        if (state != State.TICKET) {
            // Instantiate new 503-UNAVAILABLE
            ((HttpServletResponse) resp).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Rate limit reached - Throttled");

            // TODO: Should not continue the regular call on service.
        }
    }

    public void getTicket() {
        switch (state) {
            case TICKET:
                logger.info("Re-using ticket");
                break;
            case NOTICKET:
                logger.info("Trying to acquire ticket for " + this);
                if (limits.acquire(RESPONSE_TIME_MS)) {
                    logger.info("Acquired ticket");
                    state = State.TICKET;
                } else {
                    state = State.NOTICKET_AVAILABLE;
                }
                break;
            case NOTICKET_AVAILABLE:
                logger.info("No tickets available");
                break;
            case UNINITIALIZED: // ensure this does not happen
                throw new IllegalStateException("Should not receive request before init");
        }
    }
}
