import edu.utwente.aop.tools.RateLimiter;
import net.winstone.core.ServletConfiguration;
import net.winstone.core.SimpleRequestDispatcher;
import net.winstone.servlet.StaticResourceServlet;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import java.util.concurrent.TimeUnit;

/**
 * Ensure that the request is allowed by the RateLimiter before continuing..
 */
public aspect RateLimitingAspect percflowbelow(inWinstoneServletConfiguration()) {
    public final int RESPONSE_TIME_MS = 1000;

    private boolean hasTicket = false;

    static org.slf4j.Logger logger = LoggerFactory.getLogger(Servlet.class);
    // Allow 5 requests per limit
    private static RateLimiter limits = new RateLimiter(5, 1, TimeUnit.MINUTES);

    pointcut servletCallService(Servlet servlet): call(* Servlet.service(..)) && target(servlet);

    pointcut inWinstoneServletConfiguration(): within(ServletConfiguration);

    pointcut rateLimit(Servlet servlet): servletCallService(servlet) && inWinstoneServletConfiguration();

     // TODO: Rewrite to around and call service on different type.
    // TODO: Voor cflowbelow
    before(Servlet servlet): rateLimit(servlet) && !cflowbelow(adviceexecution() && within((...)) {
        logger.info(thisJoinPoint.toString());
        if (limits.acquire(RESPONSE_TIME_MS)) {
            hasTicket = true;
            logger.info("Acquired ticket for " + servlet);
        } else if (hasTicket) {
            logger.info("Ticket was acquired in cflow");
        } else {
            // redirect to HTTP 500..
            logger.error("Could NOT acquire ticket");
        }
    }
}
