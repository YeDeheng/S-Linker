import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

import ca.uwaterloo.cs.se.inconsistency.core.model2.ClassElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.FieldElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.MethodElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.MethodParamElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.Model;
import ca.uwaterloo.cs.se.inconsistency.core.model2.io.Model2XMLReader;

public class Graph
{
	private static Model _model;
	private static final String DB_PATH = "maven-graph-database";
	private static GraphDatabaseService graphDb;
	private static Index<Node> nodeIndexClass;
	private static Index<Node> nodeIndexMethod;
	private static Index<Node> nodeIndexField;
	private static Index<Node> nodeIndexShortField;
	private static Index<Node> nodeIndexShortMethod;
	private static Index<Node> nodeIndexShortClass;
	
	private static Index<Node> nodeParents;

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

	public static void populate(String fName) throws IOException
	{
		//System.out.println("Processing XML: " + fName);
		Model2XMLReader xmlrdf = new Model2XMLReader(fName);
		Model knownModel = xmlrdf.read();
		_model=knownModel;

		for ( ClassElement ce : knownModel.getClasses() )
		{
			Node node = createAndIndexClassElement(ce);
			createAndIndexParents(ce,node);
		}
		for(MethodElement me : knownModel.getMethods())
		{
			createAndIndexMethodElement(me);
		}
		for(FieldElement fe : knownModel.getFields())
		{
			createAndIndexFieldElement(fe);
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( DB_PATH );
		nodeIndexClass = graphDb.index().forNodes( "classes" );
		nodeIndexMethod = graphDb.index().forNodes( "methods" );
		nodeIndexField = graphDb.index().forNodes( "fields" );
		nodeIndexShortField = graphDb.index().forNodes( "short_fields" );
		nodeIndexShortMethod = graphDb.index().forNodes( "short_methods" );
		nodeIndexShortClass = graphDb.index().forNodes( "short_classes" );
		nodeParents = graphDb.index().forNodes("parents");
		
		registerShutdownHook();
		
		//Uncomment for a single XML to be appended to the graph
		///*
		String fName2 = "/home/s23subra/workspace/Java Snippet Parser/rt.xml";
		Transaction tx0 = graphDb.beginTx();
		try
		{
			populate(fName2);
			tx0.success();
		}
		
		finally
		{
			tx0.finish();
		}
		//*/
		//Uncomment to iterate over all XML files in a directory
		/*
		File xmlPath = new File("/home/s23subra/new_maven_data/xml2/");
		File[] fileList = xmlPath.listFiles();
		int i=0;
		for(File file : fileList)
		{
			i++;
			if(i>=0)
			{
				String fname = file.getAbsolutePath();
				System.out.println("Processing "+fname + " : " + i);
				Transaction tx1 = graphDb.beginTx();
				try
				{
					//if(fname.contains("general_")==true)
					populate(fname);
					tx1.success();
				}
				finally
				{
					tx1.finish();
				}
			}
		}
		*/
		shutdown();
	}

	private static void shutdown()
	{
		graphDb.shutdown();
	}

	private static void createAndIndexParents(ClassElement ce, Node node) throws IOException 
	{
		Collection<ClassElement> parentsList = ce.getParents();
		if(parentsList!=null)
		{
			for(ClassElement parent : parentsList)
			{
						Node parentNode = createAndIndexClassElement(parent);
						node.createRelationshipTo(parentNode, RelTypes.PARENT);
						parentNode.createRelationshipTo(node, RelTypes.CHILD);
						nodeParents.add( parentNode, "parent", ce.getId());
			}
		}
	}
	
	private static Node createAndIndexClassElement( ClassElement ce) throws IOException
	{
		IndexHits<Node> userNodes  = nodeIndexClass.get("id", ce.getId());
		Iterator<Node> iter = userNodes.iterator();
		if(iter.hasNext()==false)
		{
			Node node = graphDb.createNode();
			node.setProperty( "id", ce.getId() );
			node.setProperty("exactName", ce.getExactName());
			node.setProperty( "vis", ce.getVisiblity().toString() );
			node.setProperty( "isAbstract", ce.isAbstract() );
			node.setProperty( "isPrimitive", "false" );
			node.setProperty( "isInterface", ce.isInterface() );
			node.setProperty( "isExternal", ce.isExternal() );
			nodeIndexClass.add( node, "id", ce.getId() );
			nodeIndexShortClass.add(node, "short_name", ce.getExactName());
			return node;
		}
		else
		{
			Node existingNode = userNodes.getSingle();
			return existingNode;
		}
	}

	private static Node createAndIndexMethodElement( MethodElement me ) throws IOException
	{
		IndexHits<Node> methodNodes  = nodeIndexMethod.get("id", me.getId());
		if(methodNodes.hasNext()==false)
		{
			//System.out.println(me.getId());
			Node node = graphDb.createNode();
			node.setProperty( "id", me.getId() );
			node.setProperty("exactName", me.getExactName());
			node.setProperty( "vis", me.getVisiblity().toString());
			
			ClassElement parentClass = _model.getClass(me.extractClassName());
			insertParentAndReturn(RelTypes.IS_METHOD,RelTypes.HAS_METHOD, parentClass, node);
			
			ClassElement returnType = me.getReturnElement().getType();
			insertParentAndReturn(RelTypes.RETURN_TYPE, RelTypes.IS_RETURN_TYPE, returnType, node);

			node.setProperty("argCount", me.getParameters().size());
			Collection<MethodParamElement> params = me.getParameters();
			int i=0;
			for(MethodParamElement param : params)
			{
				i++;
				ClassElement paramtype = param.getType();
				insertParameter(RelTypes.PARAMETER, RelTypes.IS_PARAMETER, paramtype, node, i);
			}
			
			nodeIndexMethod.add( node, "id", me.getId() );
			nodeIndexShortMethod.add(node, "short_name", me.getExactName());
			return node;
		}
		else
			return methodNodes.getSingle();
	}

	private static Node createAndIndexFieldElement( FieldElement fe ) throws IOException
	{
		IndexHits<Node> fieldNodes  = nodeIndexField.get("id", fe.getId());
		if(fieldNodes.hasNext()==false)
		{
			//System.out.println(ce.getId());
			Node node = graphDb.createNode();
			node.setProperty( "id", fe.getId() );
			node.setProperty("exactName", fe.getExactName());
			node.setProperty( "vis", fe.getVisiblity().toString() );
			node.setProperty( "isPrimitive", "false" );
			node.setProperty( "isExternal", fe.isExternal() );
			
			ClassElement fieldType = fe.getType();
			
			insertParentAndReturn(RelTypes.IS_FIELD_TYPE, RelTypes.HAS_FIELD_TYPE, fieldType, node);
			ClassElement parentClass = _model.getClass(fe.getExactClassName());
			insertParentAndReturn(RelTypes.IS_FIELD, RelTypes.HAS_FIELD, parentClass, node);
			
			/*byte[] array = fe.convertFieldElementToByteArray();
			//System.out.println(array.length);
			node.setProperty("FEByteArray", array);*/
			nodeIndexField.add( node, "id", fe.getId() );
			nodeIndexShortField.add(node, "short_name", fe.getExactName());
			return node;
		}
		else
			return fieldNodes.getSingle();
	}

	private static void insertParentAndReturn(RelationshipType outgoing, RelationshipType incoming, ClassElement type, Node node) throws IOException
	{
		 if(type==null)
             return; 
     //System.out.println(type.getId());
     IndexHits<Node> returnNode = nodeIndexClass.get("id", type.getId());
     if(returnNode.hasNext())
     {
             Node returnTypeNode = returnNode.getSingle();
             node.createRelationshipTo(returnTypeNode, outgoing);
             returnTypeNode.createRelationshipTo(node, incoming);
     }
     else if(Convert.isPrimitive(type.getId()))
     {
             Node primitiveNode = graphDb.createNode();
             primitiveNode.setProperty( "id", type.getId() );
             primitiveNode.setProperty("exactName", type.getExactName());
             primitiveNode.setProperty( "vis", type.getVisiblity().toString() );
             primitiveNode.setProperty( "isAbstract", type.isAbstract() );
             primitiveNode.setProperty( "isInterface", type.isInterface() );
             primitiveNode.setProperty( "isExternal", type.isExternal() );
             primitiveNode.setProperty( "isPrimitive", "true" );
             
             /*type.setParentNull();
             type.setMethodsNull();
             type.setFieldsNull();
             primitiveNode.setProperty("CEByteArray", type.convertClassElementToByteArray());*/
             node.createRelationshipTo(primitiveNode, outgoing);
             primitiveNode.createRelationshipTo(node, incoming);
     }
     else
     {
             Node newReturnNode = graphDb.createNode();
             newReturnNode.setProperty( "id", type.getId() );
             newReturnNode.setProperty("exactName", type.getExactName());
             newReturnNode.setProperty( "vis", type.getVisiblity().toString() );
             newReturnNode.setProperty( "isAbstract", type.isAbstract() );
             newReturnNode.setProperty( "isInterface", type.isInterface() );
             newReturnNode.setProperty( "isExternal", type.isExternal() );
             newReturnNode.setProperty( "isPrimitive", "false" );
             /*type.setParentNull();
             type.setMethodsNull();
             type.setFieldsNull();
             newReturnNode.setProperty("CEByteArray", type.convertClassElementToByteArray());*/
             node.createRelationshipTo(newReturnNode, outgoing);
             newReturnNode.createRelationshipTo(node, incoming);
     }
	}

	private static void insertParameter(RelationshipType outgoing, RelationshipType incoming, ClassElement type, Node node, int paramIndex) throws IOException
	{
		if(type==null)
			return; 
		//System.out.println(type.getId());
		if(Convert.isPrimitive(type.getId()))
		{
			Node primitiveNode = graphDb.createNode();
			primitiveNode.setProperty( "id", type.getId() );
			primitiveNode.setProperty("exactName", type.getExactName());
			primitiveNode.setProperty( "vis", type.getVisiblity().toString() );
			primitiveNode.setProperty( "isAbstract", type.isAbstract() );
			primitiveNode.setProperty( "isInterface", type.isInterface() );
			primitiveNode.setProperty( "isExternal", type.isExternal() );
			primitiveNode.setProperty( "isPrimitive", "true" );
			
			primitiveNode.setProperty("paramIndex", paramIndex);
			/*type.setParentNull();
			type.setMethodsNull();
			type.setFieldsNull();
			primitiveNode.setProperty("CEByteArray", type.convertClassElementToByteArray());*/
			node.createRelationshipTo(primitiveNode, outgoing).setProperty("count",0);
		}
		else
		{
			Node newReturnNode = graphDb.createNode();
			newReturnNode.setProperty( "id", type.getId() );
			newReturnNode.setProperty("exactName", type.getExactName());
			newReturnNode.setProperty( "vis", type.getVisiblity().toString() );
			newReturnNode.setProperty( "isAbstract", type.isAbstract() );
			newReturnNode.setProperty( "isInterface", type.isInterface() );
			newReturnNode.setProperty( "isExternal", type.isExternal() );
			newReturnNode.setProperty( "isPrimitive", "false" );
			/*type.setParentNull();
			type.setMethodsNull();
			type.setFieldsNull();
			newReturnNode.setProperty("CEByteArray", type.convertClassElementToByteArray());*/
			newReturnNode.setProperty("paramIndex", paramIndex);
			node.createRelationshipTo(newReturnNode, outgoing).setProperty("count",0);
		}
	}

	private static void registerShutdownHook()
	{
		// Registers a shutdown hook for the Neo4j and index service instances
		// so that it shuts down nicely when the VM exits (even if you
		// "Ctrl-C" the running example before it's completed)
		Runtime.getRuntime().addShutdownHook( new Thread()
		{
			@Override
			public void run()
			{
				shutdown();
			}
		} );
	}
}