import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.index.impl.lucene.LuceneIndex;
import org.neo4j.kernel.Traversal;


public class GraphDatabase
{
	static GraphDatabaseService graphDb;
	private static String DB_PATH;
	
	public Index<Node> classIndex ;
	public Index<Node> methodIndex ;
	
	public Index<Node> shortClassIndex ;
	public Index<Node> shortMethodIndex ;
	public Index<Node> parentIndex;
	
	private static enum RelTypes implements RelationshipType
	{
		PARENT,
		CHILD,		
		IS_METHOD, 
		HAS_METHOD,
		IS_FIELD,
		HAS_FIELD,
		RETURN_TYPE, 
		IS_RETURN_TYPE, 
		PARAMETER, 
		IS_PARAMETER, 
		IS_FIELD_TYPE,
		HAS_FIELD_TYPE
	}
	
	public GraphDatabase(String input_oracle) 
	{
		DB_PATH = input_oracle;
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(DB_PATH);
		
		classIndex = graphDb.index().forNodes("classes");
		methodIndex = graphDb.index().forNodes("methods");
		
		shortClassIndex = graphDb.index().forNodes("short_classes");
		shortMethodIndex = graphDb.index().forNodes("short_methods");
		
		parentIndex = graphDb.index().forNodes("parents");
		
		//((LuceneIndex<Node>) classIndex).setCacheCapacity( "classes", 100000000 );
		//((LuceneIndex<Node>) methodIndex).setCacheCapacity( "methods", 100000000 );
		//((LuceneIndex<Node>) fieldIndex).setCacheCapacity( "fields", 1000000 );
		((LuceneIndex<Node>) shortClassIndex).setCacheCapacity( "short_fields", 500000000 );
		((LuceneIndex<Node>) shortMethodIndex).setCacheCapacity( "short_methods", 500000000 );
		//((LuceneIndex<Node>) shortFieldIndex).setCacheCapacity( "short_classes", 1000000 );
		((LuceneIndex<Node>) parentIndex).setCacheCapacity( "parents", 500000000);
		//registerShutdownHook();
	}
	
	public String getCurrentMethodName()
	{
	     StackTraceElement stackTraceElements[] = (new Throwable()).getStackTrace();
	     return stackTraceElements[1].toString();
	}
	
	public ArrayList<Node> getCandidateClassNodes(String className, HashMap<String, ArrayList<Node>> candidateNodesCache) 
	{
		long start = System.nanoTime();
		ArrayList<Node> candidateClassCollection = null;
		if(candidateNodesCache.containsKey(className))
		{
			candidateClassCollection = candidateNodesCache.get(className);
			System.out.println("cache hit class!");
		}
		else
		{
			IndexHits<Node> candidateNodes = shortClassIndex.get("short_name", className.replace(".", "$"));
			candidateClassCollection = new ArrayList<Node>();
			for(Node candidate : candidateNodes)
			{
				if(candidate!=null)
					if(((String)candidate.getProperty("vis")).equals("PUBLIC")==true || ((String)candidate.getProperty("vis")).equals("NOTSET")==true)
						candidateClassCollection.add(candidate);
			}
			candidateNodesCache.put(className, candidateClassCollection);
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + className + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return candidateClassCollection;
	}
	
	public ArrayList<Node> getCandidateMethodNodes(String methodName, HashMap<String, ArrayList<Node>> candidateMethodNodesCache) 
	{
		long start = System.nanoTime(); 
		ArrayList<Node> candidateMethodNodes = null;
		if(candidateMethodNodesCache.containsKey(methodName))
		{
			candidateMethodNodes = candidateMethodNodesCache.get(methodName);
			System.out.println("cache hit method!");
		}
		else
		{
			IndexHits<Node> candidateNodes = shortMethodIndex.get("short_name", methodName);
			candidateMethodNodes = new ArrayList<Node>();
			for(Node candidate : candidateNodes)
			{
				if(candidate!=null)
					if(((String)candidate.getProperty("vis")).equals("PUBLIC")==true || ((String)candidate.getProperty("vis")).equals("NOTSET")==true)
						candidateMethodNodes.add(candidate);
			}
			candidateMethodNodesCache.put(methodName, candidateMethodNodes);
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + methodName + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return candidateMethodNodes;
	}
	
	
	public boolean checkIfParentNode(Node parentNode, String childId)
	{
		
		/*Collection<Node> candidateChildren = new HashSet<Node>();
		if(childId.contains(".")==false)
		{
			candidateChildren.addAll(getCandidateClassNodes(childId));
		}
		else
		{
			Node candidate = classIndex.get("id", childId).getSingle();
			candidateChildren.add(candidate);
		}
		for(Node child : candidateChildren)
		{
			TraversalDescription td = Traversal.description()
					.breadthFirst()
					.relationships( RelTypes.PARENT, Direction.OUTGOING )
					.evaluator( Evaluators.excludeStartPosition() );
			Traverser traverser = td.traverse( child );
			
			for ( Path parentPath : traverser )
			{
					if(parentPath.endNode().getProperty("id").equals(parentNode.getProperty("id")))
					{
						//System.out.println("isParent");
						return true;
					}
			}
		}*/
		//System.out.println("isNotParent");
		long start = System.nanoTime(); 
		IndexHits<Node> candidateNodes = parentIndex.get("parent", childId);
		for(Node candidate : candidateNodes)
		{
			if(candidate!=null)
			{
				if(candidate.equals(parentNode))
					return true;
				else
				{
					Collection<Node> parents = getParents(candidate);
					for(Node pnode : parents)
						if(candidate.equals(pnode))
							return true;
				}
			}
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + parentNode.getProperty("id") + " | " + childId + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return false;
	}
	
	public ArrayList<String> getClassChildrenNodes(Node node)
	{
		TraversalDescription td = Traversal.description()
				.breadthFirst()
				.relationships( RelTypes.CHILD, Direction.OUTGOING )
				.evaluator( Evaluators.excludeStartPosition() );
		Traverser childTraverser = td.traverse( node );
		ArrayList<String> childCollection = new ArrayList<String>();;
		for ( Path child : childTraverser )
		{
				if(child.endNode()!=null)
					childCollection.add((String) child.endNode().getProperty("id"));
		}
		return childCollection;
	}
	
	public ArrayList<Node> getMethodNodes(Node node, HashMap<Node, ArrayList<Node>> methodNodesInClassNode)
	{
		long start = System.nanoTime(); 
		ArrayList<Node> methodsCollection = null;
		if(methodNodesInClassNode.containsKey(node))
		{
			methodsCollection = methodNodesInClassNode.get(node);
			System.out.println("Cache hit methods in class");
		}
		else
		{
			TraversalDescription td = Traversal.description()
					.breadthFirst()
					.relationships( RelTypes.HAS_METHOD, Direction.OUTGOING )
					.evaluator( Evaluators.excludeStartPosition() );
			Traverser methodTraverser = td.traverse( node );
			methodsCollection = new ArrayList<Node>();;
			for ( Path methods : methodTraverser )
			{
				if(methods.length()==1)
				{
					if(methods.endNode()!=null)
						methodsCollection.add(methods.endNode());
				}
				else
					break;
			}
			methodNodesInClassNode.put(node, methodsCollection);
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + node.getProperty("id") + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return methodsCollection;
	}
	
	public boolean checkIfClassHasMethod(Node classNode, String methodExactName)
	{
		String name = classNode.getProperty("id") + "." + methodExactName + "\\(*";
		IndexHits<Node> hits = methodIndex.query("id", name);
		if(hits.size()>0)
			return true;
		else
			return false;
	}
	
	public IndexHits<Node> getMethodNodesInClassNode (Node classNode, String methodExactName)
	{
		long start = System.nanoTime(); 
		String name = classNode.getProperty("id") + "." + methodExactName + "\\(*";
		IndexHits<Node> hits = methodIndex.query("id", name);
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + classNode.getProperty("id")+"."+methodExactName +" : " + String.valueOf((double)(end-start)/(1000000000)));
		return hits;
	}
	
	public Node getMethodContainer(Node node, HashMap<Node, Node> methodContainerCache)
	{
		long start = System.nanoTime(); 
		Node container = null;
		if(methodContainerCache.containsKey(node))
		{
			container = methodContainerCache.get(node);
			System.out.println("Cache hit method container");
		}
		else
		{
			TraversalDescription td = Traversal.description()
					.breadthFirst()
					.relationships( RelTypes.IS_METHOD, Direction.OUTGOING )
					.evaluator( Evaluators.excludeStartPosition() );
			Traverser paths = td.traverse( node );
			for ( Path path : paths )
			{
				if(path.length() == 1)
				{
					if(path.endNode()!=null)
					{
						container = path.endNode();
						methodContainerCache.put(node, container);
						break;
					}
				}
				else
					break;
			}
		}
		
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + node.getProperty("id")+ " : " + String.valueOf((double)(end-start)/(1000000000)));
		return container;
	}
	
	public Node getMethodReturn(Node node, HashMap<Node, Node> methodReturnCache)
	{
		long start = System.nanoTime(); 
		Node returnNode = null;
		if(methodReturnCache.containsKey(node))
		{
			returnNode = methodReturnCache.get(node);
			System.out.println("Cache hit method return");
		}
		else
		{
			TraversalDescription td = Traversal.description()
					.breadthFirst()
					.relationships( RelTypes.RETURN_TYPE, Direction.OUTGOING )
					.evaluator( Evaluators.excludeStartPosition() );
			Traverser traverser = td.traverse( node );
			for ( Path containerNode : traverser )
			{
				if(containerNode.length()==1)
				{
					returnNode = containerNode.endNode();
					break;
				}
				else
					break;
			}
		}
		long end = System.nanoTime();
		if(returnNode!=null)
		System.out.println(getCurrentMethodName() + " - " + node.getProperty("id") + returnNode.getProperty("id") + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return returnNode;
	}
	
	public TreeSet<Node> getMethodParams(Node node, HashMap<Node, TreeSet<Node>> methodParameterCache) 
	{
		long start = System.nanoTime();
		
		TreeSet<Node> paramNodesCollection = new TreeSet<Node>(new Comparator<Node>(){
			public int compare(Node a, Node b)
			{
				return (Integer)a.getProperty("paramIndex")-(Integer)b.getProperty("paramIndex");
			}
			
		});
		
		if(methodParameterCache.containsKey(node))
		{
			paramNodesCollection = methodParameterCache.get(node);
			System.out.println("Cache hit method parameters");
		}
		else
		{
			TraversalDescription td = Traversal.description()
					.breadthFirst()
					.relationships( RelTypes.PARAMETER, Direction.OUTGOING )
					.evaluator( Evaluators.excludeStartPosition() );
			Traverser traverser = td.traverse( node );
			
			for ( Path paramNode : traverser )
			{
				if(paramNode.length()==1)
				{
					paramNodesCollection.add(paramNode.endNode());
				}
				else
					break;
			}
			methodParameterCache.put(node, paramNodesCollection);
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + node.getProperty("id") + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return paramNodesCollection;
	}
	
	void shutdown()
	{
		System.out.println("graph shutdown");
		graphDb.shutdown();
	}
		
	
	
	public Node getUltimateParent(final Node node )
	{
		TraversalDescription td = Traversal.description()
				.breadthFirst()
				.relationships( RelTypes.PARENT, Direction.OUTGOING )
				.evaluator( Evaluators.excludeStartPosition() );
		Traverser ultimateParent =  td.traverse( node );
		Node answer = null;
		for ( Path paramNode : ultimateParent )
		{
			if(paramNode.length()==1)
			{
				answer = paramNode.endNode();
			}
		}
		return answer;
	}
	
	public ArrayList<Node> getParents(final Node node )
	{
		long start = System.nanoTime(); 
		IndexHits<Node> candidateNodes = parentIndex.get("parent", node.getProperty("id"));
		ArrayList<Node> classElementCollection = new ArrayList<Node>();
		for(Node candidate : candidateNodes)
		{
			if(candidate!=null)
			{
				if(((String)candidate.getProperty("vis")).equals("PUBLIC")==true || ((String)candidate.getProperty("vis")).equals("NOTSET")==true)
				{
					classElementCollection.add(candidate);
					classElementCollection.addAll(getParents(candidate));
				}
			}
		}
		long end = System.nanoTime();
		System.out.println(getCurrentMethodName() + " - " + node.getProperty("id") + " : " + String.valueOf((double)(end-start)/(1000000000)));
		return classElementCollection;
	}

}