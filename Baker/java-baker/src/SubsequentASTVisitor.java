import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;


import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;


class SubsequentASTVisitor extends ASTVisitor
{
	public HashMap<Node, Node> methodContainerCache;
	public HashMap<Node, Node> methodReturnCache;
	public GraphDatabase model;
	public CompilationUnit cu;
	public int cutype;
	public HashMap<String, HashMultimap<ArrayList<Integer>,Node>> methodReturnTypesMap;
	public HashMap<String, HashMultimap<ArrayList<Integer>,Node>> variableTypeMap;//holds variables, fields and method param types
	public HashMultimap<Integer, Node> printtypes;//holds node start loc and possible types
	public HashMultimap<Integer, Node> printmethods;//holds node start posns and possible methods they can be
	public HashMap<String, Integer> printTypesMap;//maps node start loc to variable names
	public HashMap<String, Integer> printMethodsMap;//holds node start locs with method names
	public Set<String> importList;
	public Stack<String> classNames;
	public String superclassname;
	public ArrayList<Object> interfaces;
	public int tolerance;
	public int MAX_CARDINALITY;
	private HashMultimap<String, String> localMethods;
	
	public void printFields()
	{
		System.out.println("methodReturnTypesMap: " + methodReturnTypesMap);
		System.out.println("variableTypeMap: " + variableTypeMap);
		System.out.println("printtypes: " + printtypes);
		System.out.println("printmethods: " + printmethods);
		System.out.println("printTypesMap: " + printTypesMap);
		System.out.println("printMethodsMap: " + printMethodsMap);
		System.out.println("possibleImportList: " + importList);
		System.out.println("localMethods: " + localMethods);
	}

	public SubsequentASTVisitor(FirstASTVisitor previousVisitor) 
	{
		model = previousVisitor.model;
		cu = previousVisitor.cu;
		cutype = previousVisitor.cutype;
		variableTypeMap = previousVisitor.variableTypeMap;
		methodReturnTypesMap = previousVisitor.methodReturnTypesMap;
		printtypes = previousVisitor.printtypes;
		printmethods = previousVisitor.printmethods;
		printTypesMap = previousVisitor.printTypesMap;
		printMethodsMap = previousVisitor.printMethodsMap;
		importList = previousVisitor.importList;
		classNames = previousVisitor.classNames;
		superclassname = previousVisitor.superclassname;
		interfaces = previousVisitor.interfaces;
		methodContainerCache = previousVisitor.methodContainerCache;
		methodReturnCache = previousVisitor.methodReturnCache;
		tolerance = previousVisitor.tolerance;
		MAX_CARDINALITY = previousVisitor.MAX_CARDINALITY;
		localMethods = previousVisitor.localMethods;
		upDateBasedOnImports();
	}
	
	private static void upDateBasedOnImports()
	{
		//Update variableTypeMap to hold only a possible import if one exists. Else leave untouched.
	}
	
	public SubsequentASTVisitor(SubsequentASTVisitor previousVisitor) 
	{
		model = previousVisitor.model;
		cu = previousVisitor.cu;
		cutype = previousVisitor.cutype;
		variableTypeMap = previousVisitor.variableTypeMap;
		methodReturnTypesMap = previousVisitor.methodReturnTypesMap;
		printtypes = previousVisitor.printtypes;
		printmethods = previousVisitor.printmethods;
		printTypesMap = previousVisitor.printTypesMap;
		printMethodsMap = previousVisitor.printMethodsMap;
		importList = previousVisitor.importList;
		classNames = previousVisitor.classNames;
		superclassname = previousVisitor.superclassname;
		interfaces = previousVisitor.interfaces;
		methodContainerCache = previousVisitor.methodContainerCache;
		methodReturnCache = previousVisitor.methodReturnCache;
		tolerance = previousVisitor.tolerance;
		MAX_CARDINALITY = previousVisitor.MAX_CARDINALITY;
		localMethods = previousVisitor.localMethods;
		upDateBasedOnImports();
	}
	
	private HashSet<Node> getNewClassElementsList(Set<Node> candidateClassNodes)
	{
		HashSet<Node> templist = new HashSet<Node>();
		int flagVar2 = 0;
		int flagVar3 = 0;
		for(Node ce: candidateClassNodes)
		{
			String name = (String) ce.getProperty("id");
			int flagVar1 = 0;
			if(importList.isEmpty() == false)
			{
				for(String importItem : importList)
				{
					if(name.startsWith(importItem) || name.startsWith("java.lang"))
					{
						templist.clear();
						templist.add(ce);
						flagVar1 = 1;
						break;
					}
				}
			}
			if(flagVar1==1)
				break;
			else if(name.startsWith("java."))
			{
				if(flagVar2==0)
				{
					templist.clear();
					flagVar2 =1;
				}
				templist.add(ce);
				flagVar3 = 1;
			}
			else
			{
				if(flagVar3 == 0)
					templist.add(ce);
			}
		}
		return templist;
	}

	private ArrayList<Integer> getScopeArray(ASTNode treeNode)
	{
		ASTNode parentNode;
		ArrayList<Integer> parentList = new ArrayList<Integer>();
		while((parentNode =treeNode.getParent())!=null)
		{
			parentList.add(parentNode.getStartPosition());
			treeNode = parentNode;
		}
		return parentList;
	}

	public boolean isLocalMethod(String methodName)
	{
		return false;
	}

	public void endVisit(MethodInvocation treeNode)
	{
		long start = System.nanoTime();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		Expression expression=treeNode.getExpression();
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getName().getStartPosition();
		if(expression==null)
		{
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			for(Node method : currentMethods)
			{
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				if(candidateReturnNodes.contains(returnNode) == true)
				{
					//System.out.println(method.getProperty("id") + " : " + returnNode.getProperty("id"));
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
				}
			}
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
		else if(expression.toString().contains("System."))
		{
			
		}
		else if(expression.getNodeType() == 2)
		{
		}
		else if(variableTypeMap.containsKey(expression.toString()))
		{
			//System.out.println("-- here " + startPosition);
			HashMultimap<ArrayList<Integer>, Node> temporaryMap1 = variableTypeMap.get(expression.toString());
			if(temporaryMap1 == null)
				return;
			ArrayList<Integer> rightScopeArray1 = getNodeSet(temporaryMap1, scopeArray);
			if(rightScopeArray1 == null)
				return;
			Set<Node> candidateClassNodes = temporaryMap1.get(rightScopeArray1);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			Set<Node> newClassNodes = new HashSet<Node>();
			for(Node method : currentMethods)
			{
				//System.out.println("here--");
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				Node parentNode = model.getMethodContainer(method, methodContainerCache);
				if(candidateClassNodes.contains(parentNode) == true && candidateReturnNodes.contains(returnNode) == true)
				{
					//System.out.println("here too -----");
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
					newClassNodes.add(parentNode);
				}
			}
			
			if(newClassNodes.size() < tolerance)
			{
				for(Node newClassNode : newClassNodes)
				{
					String possibleImport = getCorrespondingImport(newClassNode.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
			}
			temporaryMap1.removeAll(rightScopeArray1);
			temporaryMap1.putAll(rightScopeArray1, newClassNodes);
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
		else if(methodReturnTypesMap.containsKey(expression.toString()))
		{
			HashMultimap<ArrayList<Integer>, Node> temporaryMap1 = methodReturnTypesMap.get(expression.toString());
			if(temporaryMap1 == null)
				return;
			ArrayList<Integer> rightScopeArray1 = getNodeSet(temporaryMap1, scopeArray);
			if(rightScopeArray1 == null)
				return;
			Set<Node> candidateClassNodes = temporaryMap1.get(rightScopeArray1);
			candidateClassNodes = getNewClassElementsList(candidateClassNodes);
			
			HashMultimap<ArrayList<Integer>, Node> temporaryMap2 = methodReturnTypesMap.get(treeNodeString);
			if(temporaryMap2 == null)
				return;
			ArrayList<Integer> rightScopeArray2 = getNodeSet(temporaryMap2, scopeArray);
			if(rightScopeArray2 == null)
				return;
			Set<Node> candidateReturnNodes = temporaryMap2.get(rightScopeArray2);
			//System.out.println("candidateReturnNodes " + scopeArray + candidateReturnNodes);
			Set<Node> currentMethods = printmethods.get(startPosition);
			//System.out.println("currentMethods " + currentMethods);
			Set<Node> newMethodNodes = new HashSet<Node>();
			Set<Node> newReturnNodes = new HashSet<Node>();
			Set<Node> newClassNodes = new HashSet<Node>();
			
			for(Node method : currentMethods)
			{
				//System.out.println("here -- ");
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				Node parentNode = model.getMethodContainer(method, methodContainerCache);
				if(candidateClassNodes.contains(parentNode) == true && candidateReturnNodes.contains(returnNode) == true)
				{
					//System.out.println("-- here too");
					newMethodNodes.add(method);
					newReturnNodes.add(returnNode);
					newClassNodes.add(parentNode);
				}
			}
			if(newClassNodes.size() < tolerance)
			{
				for(Node newClassNode : newClassNodes)
				{
					String possibleImport = getCorrespondingImport(newClassNode.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
			}
			temporaryMap1.removeAll(rightScopeArray1);
			temporaryMap1.putAll(rightScopeArray1, newClassNodes);
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
			temporaryMap2.removeAll(rightScopeArray2);
			temporaryMap2.putAll(rightScopeArray2, newReturnNodes);
		}
		long end = System.nanoTime();
		//System.out.println(model.getCurrentMethodName() + " - " + treeNode.toString() + " : " + String.valueOf((double)(end-start)/1000000000));
	}

	private String getCorrespondingImport(String classID) 
	{
		int loc = classID.indexOf('.');
		if(loc == -1)
			return null;
		else
		{
			return(classID.substring(0, classID.lastIndexOf("."))+".*") ;
		}
	}

	private ArrayList<Integer> getNodeSet(HashMultimap<ArrayList<Integer>, Node> celist2, ArrayList<Integer> scopeArray) 
	{
		for(ArrayList<Integer> test : celist2.keySet())
		{
			if(isSubset(test, scopeArray))
				return test;
		}
		return null;
	}

	private boolean isSubset(ArrayList<Integer> test,ArrayList<Integer> scopeArray) 
	{
		if(scopeArray.containsAll(test))
			return true;
		/*else if(scopeArray.containsAll(test.subList(1, test.size())))
			return true;*/
		else
			return false;
	}

	public void endVisit(ConstructorInvocation treeNode)
	{	
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		
		Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
		
		Set<Node> currentMethods = printmethods.get(startPosition);
		
		Set<Node> newMethodNodes = new HashSet<Node>();
		
		for(Node method : currentMethods)
		{
			Node returnNode = model.getMethodReturn(method, methodReturnCache);
			if(candidateReturnNodes.contains(returnNode) == true)
			{
				newMethodNodes.add(method);
			}
		}
		printmethods.removeAll(startPosition);
		printmethods.putAll(startPosition, newMethodNodes);
	}

	public void endVisit(SuperConstructorInvocation treeNode)
	{	
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		
		Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
		
		Set<Node> currentMethods = printmethods.get(startPosition);
		
		Set<Node> newMethodNodes = new HashSet<Node>();
		
		for(Node method : currentMethods)
		{
			Node returnNode = model.getMethodReturn(method, methodReturnCache);
			if(candidateReturnNodes.contains(returnNode) == true)
			{
				newMethodNodes.add(method);
			}
		}
		printmethods.removeAll(startPosition);
		printmethods.putAll(startPosition, newMethodNodes);
	}

	public void endVisit(SuperMethodInvocation treeNode)
	{
		String treeNodeString = treeNode.toString();
		int startPosition = treeNode.getStartPosition();
		ArrayList<Integer> scopeArray = getScopeArray(treeNode);
		//System.out.println(treeNodeString);
		if(methodReturnTypesMap.containsKey(treeNodeString))
		{
			Set<Node> candidateReturnNodes = methodReturnTypesMap.get(treeNodeString).get(scopeArray);
			
			Set<Node> currentMethods = printmethods.get(startPosition);
			
			Set<Node> newMethodNodes = new HashSet<Node>();
			
			for(Node method : currentMethods)
			{
				Node returnNode = model.getMethodReturn(method, methodReturnCache);
				if(candidateReturnNodes.contains(returnNode) == true)
				{
					newMethodNodes.add(method);
				}
			}
			printmethods.removeAll(startPosition);
			printmethods.putAll(startPosition, newMethodNodes);
		}
	}

	public JSONObject printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		for(Integer key : printtypes.keySet())
		{
			int flag = 0;
			String cname = null;
			List<String> namelist = new ArrayList<String>();
			if(printtypes.get(key).size() < MAX_CARDINALITY)
			{
				for(Node type_name:printtypes.get(key))
				{
					int isprimitive=0;
					for(String primitive_type : primitive)
					{
						if(((String)type_name.getProperty("id")).equals(primitive_type) == true)
						{
							isprimitive = 1;
							break;
						}
					}
					if(isprimitive == 0)
					{
						String nameOfClass = (String)type_name.getProperty("id");
						namelist.add("\""+nameOfClass+"\"");
						if(flag == 0)
						{
							cname = (String) type_name.getProperty("exactName");
							flag = 1;
						}
					}
	
				}
				if(namelist.isEmpty() == false)
				{
					JSONObject json = new JSONObject();
					json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
					json.accumulate("precision", Integer.toString(namelist.size()));
					json.accumulate("name",cname);
					json.accumulate("elements",namelist);
					json.accumulate("type","api_type");
					json.accumulate("character", Integer.toString(key));
					main_json.accumulate("api_elements", json);
				}
			}
		}
		for(Integer key:printmethods.keySet())
		{
			List<String> namelist = new ArrayList<String>();
			String mname = null;
			if(printmethods.get(key).size() < MAX_CARDINALITY)
			{
				for(Node method_name : printmethods.get(key))
				{
					String nameOfMethod = (String)method_name.getProperty("id");
					namelist.add("\""+nameOfMethod+"\"");
					mname=(String) method_name.getProperty("exactName");
				}
				if(namelist.isEmpty() == false)
				{
					JSONObject json = new JSONObject();
					json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
					json.accumulate("precision", Integer.toString(namelist.size()));
					json.accumulate("name",mname);
					json.accumulate("elements",namelist);
					json.accumulate("type","api_method");
					json.accumulate("character", Integer.toString(key));
					main_json.accumulate("api_elements", json);
				}
			}
		}
		if(main_json.isNull("api_elements"))
		{
			String emptyJSON = "{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ;
			JSONObject ret = new JSONObject();
			try 
			{
				ret = new JSONObject(emptyJSON);
			} 
			catch (ParseException e) 
			{
				e.printStackTrace();
			}
			return(ret);
		}
		else
		{
			return(main_json);
		}
	}

	public void checkForNull()
	{
		for(Integer key : printtypes.keySet())
			for(Node type_name:printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		for(Integer key : printmethods.keySet())
			for(Node method_name:printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
	}


}