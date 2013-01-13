/**
 * Created with IntelliJ IDEA.
 * User: kockt
 * Date: 13-01-13
 * Time: 17:09
 * To change this template use File | Settings | File Templates.
 */
public aspect TraceRequestFlow {

    pointcut was_called(Object o):
            target(o) && call(* *(..)) && within(net.winstone);

    before() calling: was_called(o) {
        System.out.println(o);
    }

}
