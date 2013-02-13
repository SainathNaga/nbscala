package org.netbeans.modules.scala.sbt.nodes

import java.awt.Image
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import javax.swing.Action
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import org.netbeans.api.project.Project
import org.netbeans.api.project.ProjectManager
import org.netbeans.modules.scala.sbt.project.ProjectConstants
import org.netbeans.modules.scala.sbt.project.SBTProject
import org.netbeans.modules.scala.sbt.project.SBTResolver
import org.netbeans.spi.project.ui.support.NodeFactory
import org.netbeans.spi.project.ui.support.NodeList
import org.openide.filesystems.FileUtil
import org.openide.loaders.DataObjectNotFoundException
import org.openide.nodes.AbstractNode
import org.openide.nodes.ChildFactory
import org.openide.nodes.Children
import org.openide.nodes.Node
import org.openide.util.ChangeSupport
import org.openide.util.Exceptions
import org.openide.util.ImageUtilities
import org.openide.util.NbBundle

class DepProjectsNodeFactory extends NodeFactory {
  def createNodes(project: Project): NodeList[_] = new DepProjectsNodeFactory.ProjectsNodeList(project)
}

object DepProjectsNodeFactory {
  private val DEP_PROJECTS = "dep-projects"
  private val ICON_LIB_BADGE = ImageUtilities.loadImage("org/netbeans/modules/java/j2seproject/ui/resources/libraries-badge.png")    //NOI18N
    
  private class ProjectsNodeList(project: Project) extends NodeList[String] with PropertyChangeListener {
    private val cs = new ChangeSupport(this)
    private lazy val sbtResolver = {
      val x = project.getLookup.lookup(classOf[SBTResolver])
      x.addPropertyChangeListener(this)
      x
    }
    
    def keys: java.util.List[String] = {
      val theKeys = new java.util.ArrayList[String]()
      theKeys.add(DEP_PROJECTS)
      theKeys
    }

    /**
     * return null if node for this key doesn't exist currently
     */
    def node(key: String): Node = {
      if (sbtResolver.getDependenciesProjects.length == 0) {
        null
      } else {
        try {
          new ProjectNode(project)
        } catch {
          case ex: DataObjectNotFoundException => Exceptions.printStackTrace(ex); null
        }
      }
    }

    def addNotify() {
      // addNotify will be called only when if node(key) returns non-null and node is 
      // thus we won't sbtResolver.addPropertyChangeListener(this) here
    }

    def removeNotify() {
      sbtResolver.removePropertyChangeListener(this)
    }
    
    def addChangeListener(l: ChangeListener) {
      cs.addChangeListener(l)
    }

    def removeChangeListener(l: ChangeListener) {
      cs.removeChangeListener(l)
    }

    def propertyChange(evt: PropertyChangeEvent) {
      evt.getPropertyName match {
        case SBTResolver.DESCRIPTOR_CHANGE => 
          // The caller holds ProjectManager.mutex() read lock
          SwingUtilities.invokeLater(new Runnable() {
              def run() {
                keys
                cs.fireChange
              }
            })
        case _ =>
      }
    }
  }
  
  private class ProjectNode(project: Project) extends AbstractNode(Children.create(new ProjectsChildFactory(project), true)) {
    private val DISPLAY_NAME = NbBundle.getMessage(classOf[DepProjectsNodeFactory], "CTL_DepProjectsNode")

    override
    def getDisplayName: String = DISPLAY_NAME

    override
    def getName: String = ProjectConstants.NAME_DEP_PROJECTS

    override
    def getIcon(tpe: Int) = getIcon(false, tpe)

    override
    def getOpenedIcon(tpe: Int) = getIcon(true, tpe)

    private def getIcon(opened: Boolean, tpe: Int) = ImageUtilities.mergeImages(Icons.getFolderIcon(opened), getBadge, 7, 7)
    private def getBadge: Image = ICON_LIB_BADGE

    override
    def getActions(context: Boolean): Array[Action] = Array[Action]()
  }
  
  private class ProjectsChildFactory(parentProject: Project) extends ChildFactory.Detachable[Project] with PropertyChangeListener {
    private lazy val sbtResolver = {
      val x = parentProject.getLookup.lookup(classOf[SBTResolver])
      x.addPropertyChangeListener(this)
      x
    }

    override 
    protected def createKeys(toPopulate: java.util.List[Project]): Boolean = {
      val toSort = new java.util.TreeMap[String, Project]()
      try {
        val projectFos = sbtResolver.getDependenciesProjects map FileUtil.toFileObject
        for (projectFo <- projectFos) {
          ProjectManager.getDefault.findProject(projectFo) match {
            case x: SBTProject => toSort.put(x.getName, x)
            case _ =>
          }
        }
      } catch {
        case ex: IOException => Exceptions.printStackTrace(ex)
        case ex: IllegalArgumentException => Exceptions.printStackTrace(ex)
      }
      
      toPopulate.addAll(toSort.values)
      true
    }
    
    override
    protected def createNodeForKey(key: Project): Node = new SubProjectNode(key)

    override 
    protected def addNotify() {
      // addNotify will be called only when if node(key) returns non-null and node is 
      // thus we won't sbtResolver.addPropertyChangeListener(this) here
      super.addNotify
    }

    override
    protected def removeNotify() {
      sbtResolver.removePropertyChangeListener(this)
      super.removeNotify
    }
    
    def propertyChange(evt: PropertyChangeEvent) {
      evt.getPropertyName match {
        case SBTResolver.DESCRIPTOR_CHANGE => 
          // The caller holds ProjectManager.mutex() read lock
          SwingUtilities.invokeLater(new Runnable() {
              def run() {
                ProjectsChildFactory.this.refresh(true)
              }
            })
        case _ =>
      }
    }
  }
  
}