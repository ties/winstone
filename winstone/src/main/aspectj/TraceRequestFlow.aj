import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.SourceLocation;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: kockt
 * Date: 13-01-13
 * Time: 17:09
 * To change this template use File | Settings | File Templates.
 */
public aspect TraceRequestFlow {
    private static Map<SourceLocation, SourceLocation> calls;

    pointcut inWinstone(): within(net.winstone.*) || within(net.winstone..*);

    pointcut internalCall(Object o): target(o) && call(* *(..)) && !within(TraceRequestFlow);


    before(Object o): internalCall(o) && inWinstone() {
        calls.put(thisEnclosingJoinPointStaticPart.getSourceLocation(), thisJoinPoint.getSourceLocation());
        // System.out.println(String.format("%s:%s:%s,%s:%s", thisEnclosingJoinPointStaticPart.getSourceLocation().getFileName(), thisEnclosingJoinPointStaticPart.getSignature().getName(), thisEnclosingJoinPointStaticPart.getSourceLocation().getLine(), thisJoinPoint.getSourceLocation().getFileName(), thisJoinPoint.getSignature().getName(), thisJoinPoint.getSourceLocation().getLine()));
    }
}
