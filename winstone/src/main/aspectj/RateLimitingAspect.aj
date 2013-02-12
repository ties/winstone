import edu.utwente.aop.tools.RateLimiter;
import net.winstone.core.ServletConfiguration;
import net.winstone.core.SimpleRequestDispatcher;
import net.winstone.servlet.StaticResourceServlet;
import org.aspectj.lang.Aspects;
import org.aspectj.lang.Aspects14;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import java.util.concurrent.TimeUnit;

import net.winstone.core.listener.RequestHandlerThread;

/**
 * Ensure that the request is allowed by the RateLimiter before continuing..
 */
public aspect RateLimitingAspect  percflow(processRequest()) {
    private enum State { UNINITIALIZED, NOTICKET, TICKET, NOTICKET_AVAILABLE };

    public final int RESPONSE_TIME_MS = 1000;

    private State state = State.UNINITIALIZED;

    static org.slf4j.Logger logger = LoggerFactory.getLogger(Servlet.class);
    // Allow 5 requests per limit
    private static RateLimiter limits = new RateLimiter(5, 1, TimeUnit.MINUTES);

    pointcut processRequest(): call(* processRequest(..)) && within(RequestHandlerThread);

    pointcut servletCallService(Servlet servlet): call(* Servlet.service(..)) && target(servlet);

    pointcut inWinstoneServletConfiguration(): within(ServletConfiguration);

    pointcut rateLimit(Servlet servlet): servletCallService(servlet) && inWinstoneServletConfiguration();

    before(): processRequest() {
        logger.info("Processrequest!");
        state = State.NOTICKET;
    }

     // TODO: Rewrite to around and call service on different type.
    // TODO: Voor cflowbelow
    before(Servlet servlet): rateLimit(servlet) {
        logger.info(this.toString());

        switch (state) {
            case TICKET:
                logger.info("Re-using ticket");
                break;
            case NOTICKET:
                logger.info("Trying to acquire ticket for " + servlet);
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
