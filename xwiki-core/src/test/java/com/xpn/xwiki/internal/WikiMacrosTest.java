package com.xpn.xwiki.internal;

import org.hibernate.HibernateException;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.ObservationManager;
import org.xwiki.observation.event.DocumentSaveEvent;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.wikibridge.WikiMacroManager;
import org.xwiki.rendering.syntax.Syntax;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.AbstractBridgedComponentTestCase;
import com.xpn.xwiki.user.api.XWikiRightService;

public class WikiMacrosTest extends AbstractBridgedComponentTestCase
{
    private WikiMacroManager macroManager;

    private XWiki xwiki;

    private XWikiRightService rightService;

    private EventListener wikiMacroEventListener;

    private XWikiDocument macroDocument;

    private BaseObject macroObject;

    @Before
    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        this.macroManager = getComponentManager().lookup(WikiMacroManager.class);
        this.wikiMacroEventListener = getComponentManager().lookup(EventListener.class, "wikimacrolistener");

        this.xwiki = getMockery().mock(XWiki.class);
        this.rightService = getMockery().mock(XWikiRightService.class);
        getContext().setWiki(this.xwiki);

        getMockery().checking(new Expectations()
        {
            {
                allowing(xwiki).getRightService();
                will(returnValue(rightService));
            }
        });

        this.macroDocument = new XWikiDocument(new DocumentReference("wiki", "Space", "Page"));
        this.macroDocument.setSyntax(Syntax.XWIKI_2_0);
        this.macroDocument.setNew(false);
        this.macroObject = new BaseObject();
        this.macroObject.setXClassReference(new DocumentReference("wiki", "XWiki", "WikiMacroClass"));
        this.macroObject.setStringValue("id", "macroid");
        this.macroObject.setLargeStringValue("code", "code");
        this.macroDocument.addXObject(macroObject);
    }

    private ComponentManager getWikiComponentManager() throws Exception
    {
        return getComponentManager().lookup(ComponentManager.class, "wiki");
    }

    private ComponentManager getUserComponentManager() throws Exception
    {
        return getComponentManager().lookup(ComponentManager.class, "user");
    }

    @Test
    public void testSaveWikiMacro() throws Exception
    {
        final DocumentSaveEvent documentSaveEvent = new DocumentSaveEvent("wiki.Space.Name");

        getMockery().checking(new Expectations() {{
            allowing(xwiki).getDocument(with(equal(macroDocument.getDocumentReference())), with(any(XWikiContext.class))); will(returnValue(macroDocument));
            allowing(rightService).hasAccessLevel(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(XWikiContext.class))); will(returnValue(true));
        }});
        
        this.macroObject.setStringValue("visibility", "Current Wiki");
        
        this.wikiMacroEventListener.onEvent(documentSaveEvent, this.macroDocument, getContext());
        
        Macro testMacro = getWikiComponentManager().lookup(Macro.class, "macroid");
        
        Assert.assertEquals("macroid", testMacro.getDescriptor().getId().getId());
        
        try {
            testMacro = getComponentManager().lookup(Macro.class, "macroid");
        
            Assert.fail("Found macro with wiki visibility in global componenet manager");
        } catch (ComponentLookupException expected) {
        }
    }

    @Test
    public void testUnRegisterWikiMacroWithDifferentVisibilityKeys() throws Exception
    {
        final DocumentSaveEvent documentSaveEvent = new DocumentSaveEvent("wiki.Space.Name");
        
        getMockery().checking(new Expectations() {{
            allowing(xwiki).getDocument(with(equal(macroDocument.getDocumentReference())), with(any(XWikiContext.class))); will(returnValue(macroDocument));
            allowing(rightService).hasAccessLevel(with(any(String.class)), with(any(String.class)), with(any(String.class)), with(any(XWikiContext.class))); will(returnValue(true));
        }});
        
        this.macroObject.setStringValue("visibility", "Current User");

        getContext().setUser("XWiki.user");
        
        this.wikiMacroEventListener.onEvent(documentSaveEvent, this.macroDocument, getContext());
        
        Macro testMacro = getUserComponentManager().lookup(Macro.class, "macroid");
        
        Assert.assertEquals("macroid", testMacro.getDescriptor().getId().getId());

        // register with another user

        getContext().setUser("XWiki.user2");

        this.wikiMacroEventListener.onEvent(documentSaveEvent, this.macroDocument, getContext());
        
        testMacro = getUserComponentManager().lookup(Macro.class, "macroid");
        
        Assert.assertEquals("macroid", testMacro.getDescriptor().getId().getId());
        
        // validate that the macro as been properly unregistered for former user
        getContext().setUser("XWiki.user");

        try {
            testMacro = getUserComponentManager().lookup(Macro.class, "macroid");
        
            Assert.fail("The macro has not been properly unregistered");
        } catch (ComponentLookupException expected) {
        }
    }
}
