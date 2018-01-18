package xltutil.runner.helper;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import xltutil.annotation.TestTargets;

/**
 * Tests the implementation of {@link AnnotationRunnerHelper}.
 */
public class AnnotationRunnerHelperTest
{
    @Test
    public void testGetTestTargets_BaseClass()
    {
        final List<TestTargets> list = AnnotationRunnerHelper.getTestTargets(AbstractFoo.class);
        Assert.assertEquals(1, list.size());
        Assert.assertArrayEquals(new String[]
        {
          "a", "b"
        }, list.get(0).value());
    }

    @Test
    public void testGetTestTargets_InheritedClass()
    {
        final List<TestTargets> list = AnnotationRunnerHelper.getTestTargets(FooImpl.class);
        Assert.assertEquals(1, list.size());
        Assert.assertArrayEquals(new String[]
        {
          "a", "b"
        }, list.get(0).value());
    }

    @Test
    public void testGetTestTargets_InheritedClassWithOverride()
    {
        final List<TestTargets> list = AnnotationRunnerHelper.getTestTargets(BarImpl.class);
        Assert.assertEquals(1, list.size());
        Assert.assertArrayEquals(new String[]
        {
          "c"
        }, list.get(0).value());
    }

    @TestTargets(
    {
      "a", "b"
    })
    static abstract class AbstractFoo
    {

    }

    static class FooImpl extends AbstractFoo
    {

    }

    @TestTargets(
    {
      "c"
    })
    static class BarImpl extends FooImpl
    {

    }
}
