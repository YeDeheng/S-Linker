import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.json.JSONObject;
import org.neo4j.graphdb.Node;


class MyNewASTVisitor extends ASTVisitor
{
	private GraphDatabase model;
	private CompilationUnit cu;
	private int cutype;
	private HashMultimap<String, Node> globalmethods=HashMultimap.create();//holds method return types for chains
	//private HashMultimap<String, Node> globaltypes=HashMultimap.create();//holds variables, fields and method param types
	private HashMap<String, HashMultimap<ArrayList<Integer>,Node>> globaltypes2=new HashMap<String, HashMultimap<ArrayList<Integer>,Node>>();//holds variables, fields and method param types
	private HashMultimap<Integer, Node> printtypes=HashMultimap.create();//holds node start loc and possible types
	private HashMultimap<Integer, Node> printmethods=HashMultimap.create();//holds node start posns and possible methods they can be
	private HashMap<String, Integer> printTypesMap=new HashMap<String, Integer>();//maps node start loc to variable names
	private HashMap<String, Integer> printMethodsMap=new HashMap<String, Integer>();//holds node start locs with method names
	private HashMultimap<Integer, Integer> affectedTypes = HashMultimap.create();//holds node start locs with list of start locs they influence
	private HashMultimap<Integer, Integer> affectedMethods = HashMultimap.create();//holds node start locs with list of start locs they influence
	private Set<String> importList = new HashSet<String>();
	private String classname = null;
	private String superclassname=null;
	private ArrayList<Object> interfaces=new ArrayList<Object>();
	private int tolerance = 3;

	private Collection<Node> getNewCeList(Collection<Node> celist)
	{
		Collection<Node> templist = new HashSet<Node>();
		int flagVar2 = 0;
		int flagVar3 = 0;
		for(Node ce: celist)
		{
			String name = (String) ce.getProperty("id");
			int flagVar1 = 0;
			int size = importList.size();
			if(size != 0)
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
				//System.out.println(name + "~~~~~~~~~~~~~~~~~~~~~~~");
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

	MyNewASTVisitor(GraphDatabase db, CompilationUnit cu, int cutype) 
	{
		this.model=db;
		this.cu=cu;
		this.cutype=cutype;
		//db.test();
	}

	public void printFields()
	{
		/*System.out.println("globalmethods"+globalmethods);
		System.out.println("globaltypes"+globaltypes2);
		System.out.println("printtypes"+printtypes);
		System.out.println("printmethods"+printmethods);
		System.out.println("printTypesMap"+printTypesMap);
		System.out.println("printMethodsMap"+printMethodsMap);
		System.out.println("affectedTypes"+affectedTypes);
		System.out.println("affectedMethods"+affectedMethods);
		System.out.println("possibleImportList"+importList);*/
	}

	private ArrayList<Integer> getScopeArray(ASTNode node)
	{
		ASTNode actualNode = node;
		ASTNode parentNode;
		ArrayList<Integer> scopeList = new ArrayList<Integer>();
		while((parentNode =actualNode.getParent())!=null)
		{
			scopeList.add(parentNode.getStartPosition());
			actualNode = parentNode;
		}
		return scopeList;
	}

	public void endVisit(VariableDeclarationStatement node)
	{
		for(int j=0;j<node.fragments().size();j++)
		{
			//System.out.println(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString() + " : "+getScopeArray(node).toString() + "$$$$$");
			ArrayList<Integer> scopeArray = getScopeArray(node);
			HashMultimap<ArrayList<Integer>, Node> temp = null;
			if(globaltypes2.containsKey(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString()))
			{
				temp = globaltypes2.get(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
			celist = getNewCeList(celist);
			for(Node ce : celist)
			{
				temp.put(scopeArray, ce);
				if(celist.size() < tolerance)
				{
					String possibleImport = checkAndSlice(ce.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
				printtypes.put(node.getType().getStartPosition(), ce);
				if(celist.size() < tolerance)
				{
					String possibleImport = checkAndSlice(ce.getProperty("id").toString());
					if(possibleImport!=null)
						importList.add(possibleImport);
				}
				printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
			}
			globaltypes2.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), temp);
		}
	}

	private String checkAndSlice(String string) 
	{
		int loc = string.indexOf('.');
		if(loc==-1)
			return null;
		else
		{
			return(string.substring(0, string.lastIndexOf("."))+".*") ;
		}
			
	}
	public boolean visit(EnhancedForStatement node)
	{
		ArrayList<Integer> scopeArray = getScopeArray(node.getParent());
		//System.out.println("----> "+node.getParameter().getType().toString()+":"+node.getParameter().getName().toString() + scopeArray);
		HashMultimap<ArrayList<Integer>, Node> temp = null;
		if(globaltypes2.containsKey(node.getParameter().getName().toString()))
		{
			temp = globaltypes2.get(node.getParameter().getName().toString());
		}
		else
		{
			temp = HashMultimap.create();
		}
		Collection<Node> celist=model.getCandidateClassNodes(node.getParameter().getType().toString());
		celist = getNewCeList(celist);
		for(Node ce : celist)
		{
			//System.out.println(ce.getProperty("id"));
			temp.put(scopeArray, ce);
			if(celist.size() < tolerance)
			{
				String possibleImport = checkAndSlice(ce.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			if(celist.size() < tolerance)
			{
				String possibleImport = checkAndSlice(ce.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(node.getParameter().getType().getStartPosition(), ce);
			printTypesMap.put(node.getParameter().getName().toString(), node.getParameter().getType().getStartPosition());
		}
		globaltypes2.put(node.getParameter().getName().toString(), temp);
		return true;
	}

	public void endVisit(ForStatement node)
	{
		/*for(int j=0;j<node.initializers().size();j++)
		{
			//System.out.println(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString() + " : "+getScopeArray(node).toString() + "$$$$$");
			ArrayList<Integer> scopeArray = getScopeArray(node);
			HashMultimap<ArrayList<Integer>, Node> temp = null;
			if(globaltypes2.containsKey(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString()))
			{
				temp = globaltypes2.get(((VariableDeclarationFragment)node.initializers().get(j)).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			Collection<Node> celist=model.getCandidateClassNodes(((VariableDeclarationFragment)node.initializers().get(j)).getType().toString());
			celist = getNewCeList(celist);
			for(Node ce : celist)
			{
						temp.put(scopeArray, ce);
						printtypes.put(((VariableDeclarationFragment)node.initializers().get(j)).getStartPosition(), ce);
						printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
			}
			globaltypes2.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), temp);
		}*/
	}

	public void endVisit(FieldDeclaration node) 
	{
		for(int j=0;j<node.fragments().size();j++)
		{
			//System.out.println(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString()+ ": " + getScopeArray(node) + "-----");
			HashMultimap<ArrayList<Integer>, Node> temp = null;
			ArrayList<Integer> scopeArray = getScopeArray(node);
			if(globaltypes2.containsKey(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString()))
			{
				temp = globaltypes2.get(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			if(node.getType().getNodeType()==74)
			{
				Collection<Node> celist=model.getCandidateClassNodes(((ParameterizedType)node.getType()).getType().toString());
				celist = getNewCeList(celist);
				for(Node ce : celist)
				{
					temp.put(scopeArray, ce);
					if(celist.size() < tolerance)
					{
						String possibleImport = checkAndSlice(ce.getProperty("id").toString());
						if(possibleImport!=null)
						{
							importList.add(possibleImport);
						}
					}
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			else
			{
				Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
				celist = getNewCeList(celist);
				for(Node ce : celist)
				{
					temp.put(scopeArray, ce);
					if(celist.size() < tolerance)
					{
						String possibleImport = checkAndSlice(ce.getProperty("id").toString());
						if(possibleImport!=null)
						{
							importList.add(possibleImport);
						}
					}
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			//Collection<>removeUnImported(temp);
			globaltypes2.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), temp);
		}
	}

	public void endVisit(MethodInvocation node)
	{
		Expression e=node.getExpression();
		//System.out.println(e.toString());
		if(e==null)
		{
			if(superclassname!=null)
			{	//System.out.println("###"+node.getName().toString()+"  1.1");
				/*
				 * Handles inheritance, where methods from Superclasses can be directly called
				 */
				Collection<Node> celist=model.getCandidateClassNodes(superclassname);
				celist = getNewCeList(celist);
				for(Node ce : celist)
				{
					Collection<Node> melist=model.getMethodNodes(ce);
					for(Node me : melist)
					{
						if(((String)me.getProperty("exactName")).equals(node.getName().toString()))
						{
							if(matchParams(me, node.arguments())==true)
							{
								if(celist.size() < tolerance)
								{
									String possibleImport = checkAndSlice(ce.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}
								printtypes.put(node.getStartPosition(),ce);
								printmethods.put(node.getStartPosition(), me);
								Node retElement = model.getMethodReturn(me);
								if(retElement!=null)
									globalmethods.put(node.getName().toString(),retElement);
							}
						}
					}
				}
			}
			else
			{
				//System.out.println("###"+node.getName().toString()+"  1.2");
				/*
				 * Might be user declared helper functions or maybe object reference is assumed to be obvious in the snippet
				 */
				Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
				for(Node me : melist)
				{
					if(matchParams(me, node.arguments())==true)
					{
						Node containerClass = model.getMethodContainer(me);
						if(melist.size() < tolerance)
						{
							String possibleImport = checkAndSlice(containerClass.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(node.getName().getStartPosition(), containerClass);
						printmethods.put(node.getName().getStartPosition(), me);
						Node retElement = model.getMethodReturn(me);
						if(retElement!=null)
							globalmethods.put(node.toString(),retElement);
					}
				}
			}
		}
		else if(e.toString().contains("System."))
		{

		}
		else if(e.getNodeType() == 2)
		{
			//System.out.println("array method");
		}
		else if(globaltypes2.containsKey(e.toString()))
		{
			String exactname=null;
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();

			HashMultimap<ArrayList<Integer>, Node> celist_temp = globaltypes2.get(e.toString());
			ArrayList<Integer> scopeArray = getNodeSet(celist_temp, getScopeArray(node));
			if(scopeArray == null)
				return;
			Set<Node> celist = celist_temp.get(scopeArray);
			printMethodsMap.put(node.toString(),node.getStartPosition());
			affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());

			for(Node ce:celist)
			{
				exactname=(String) ce.getProperty("exactName");
				Collection<Node> melist = model.getMethodNodes(ce);
				for(Node me : melist)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName().toString()))
					{
						if(matchParams(me, node.arguments())==true)
							methods.add(me);
					}
				}
				for(Node m : methods)
				{
					//System.out.println(e.toString()+" : "+node.getName().toString() +" : "+m.getProperty("id"));
					printmethods.put(node.getName().getStartPosition(), m);
					Node fcname=model.getMethodContainer(m);
					//if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(exactname)==true)
					if(fcname!=null)
						clist.add(fcname);
					Node retElement = model.getMethodReturn(m);
					if(retElement!=null)
						globalmethods.put(node.toString(), retElement);
				}
				if(methods.isEmpty())
				{
					Set<Node> methods2=new HashSet<Node>();
					//System.out.println("EMPTY: " + node.getName().toString()+" : "+ ce.getProperty("id"));
					Collection<Node> parentList = model.getParents(ce);
					parentList = getNewCeList(parentList);
					for(Node pe: parentList)
					{
						//System.out.println(pe.getProperty("id"));
						Collection<Node> melist1 = model.getMethodNodes(pe);
						for(Node me : melist1)
						{
							if(((String)me.getProperty("exactName")).equals(node.getName().toString()))
							{
								if(matchParams(me, node.arguments())==true)
								{
									//System.out.println(me.getProperty("id"));
									methods2.add(me);
									//System.out.println(node.arguments() + " : " + me.getProperty("id"));
								}
							}
						}
						for(Node m : methods2)
						{
							printmethods.put(node.getName().getStartPosition(), m);
							Node fcname=model.getMethodContainer(m);
							//if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(exactname)==true)
							if(fcname!=null)
								clist.add(fcname);
							Node retElement = model.getMethodReturn(m);
							if(retElement!=null)
								globalmethods.put(node.toString(), retElement);
						}
					}
				}
			}
			//clist has the list of class names extracted from the methodname that match the exactname
			//use that to replace previously assigned values

			if(clist.isEmpty()==false)
			{
				globaltypes2.get(e.toString()).replaceValues(scopeArray,clist);
				//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
				printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
				//change affected types of e.toString() too
				//System.out.println("1&&&"+clist);
				for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
				{
					printtypes.replaceValues(affectedNode, clist);
					//System.out.println("0");
				}

				for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
				{
					Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
					//System.out.println("1:"+affectedNode);
					printmethods.replaceValues(affectedNode, temp);
				}
				if(printtypes.containsKey(node.getExpression().getStartPosition()))
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
				else
				{
					
					printtypes.putAll(node.getExpression().getStartPosition(), clist);
					for(Node cnode : clist)
					{
						if(clist.size() < tolerance)
						{
							String possibleImport = checkAndSlice(cnode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
					}
				}
				//System.out.println(affectedTypes);

			}
		}
		else if(e.toString().matches("[A-Z][a-zA-Z]*"))
		{
			//System.out.println("###"+node.getName().toString()+"  3.1");
			String exactname="";
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			Collection<Node> celist=model.getCandidateClassNodes(e.toString());
			celist = getNewCeList(celist);
			for(Node ce : celist)
			{
				exactname=(String) ce.getProperty("exactName");
				Collection<Node> melist = model.getMethodNodes(ce);
				for(Node me : melist)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName().toString()))
					{
						if(matchParams(me, node.arguments())==true)
						{
							printmethods.put(node.getName().getStartPosition(), me);
							clist.add(ce);
							Node retElement = model.getMethodReturn(me);
							if(retElement!=null)
								globalmethods.put(node.toString(),retElement);
						}
					}
				}	
			}



			if(clist.isEmpty()==false)
			{
				
					//globaltypes.replaceValues(e.toString(),clist);
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					//System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						//System.out.println("0");
					}

					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
				 
				if(printtypes.containsKey(node.getExpression().getStartPosition()))
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
				else
				{
					printtypes.putAll(node.getExpression().getStartPosition(), clist);
					for(Node cnode:clist)
					{
						if(clist.size() < tolerance)
						{
							String possibleImport = checkAndSlice(cnode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
					}
				}
				//System.out.println(affectedTypes);

			}
		}
		else if(globalmethods.containsKey(e.toString()))
		{
			String exactname="";
			Set<Node> celist=globalmethods.get(e.toString());
			System.out.println("###"+node.getName().toString()+ " : " + celist);
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			//affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			//affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(Node ce : celist)
			{
				exactname=(String) ce.getProperty("exactName");
				Collection<Node> melist = model.getMethodNodes(ce);
				for(Node me : melist)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName().toString()) && me!=null)
					{	
						if(matchParams(me, node.arguments())==true)
							methods.add(me);
					}
				}
				for(Node m : methods)
				{
					printmethods.put(node.getName().getStartPosition(), m);
					Node fcname=model.getMethodContainer(m);
					if(fcname!=null && ((String)fcname.getProperty("exactName")).equals(exactname)==true)
						clist.add(fcname);
					Node retElement = model.getMethodReturn(m);
					if(retElement!=null)
						globalmethods.put(node.toString(),retElement);
				}
			}

			if(clist.isEmpty()==false)
			{
				globalmethods.replaceValues(e.toString(),clist);
				//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
				//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
				//change affected types of e.toString() too
				//System.out.println("1&&&"+clist);
				for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
				{
					printtypes.replaceValues(affectedNode, clist);
					//System.out.println("0");
				}

				for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
				{
					Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
					//System.out.println("1:"+affectedNode);
					printmethods.replaceValues(affectedNode, temp);
				}
				printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
				//System.out.println(affectedTypes);
			}
		}
		else
		{
			//System.out.println("###"+node.getName().toString()+"--"+e.toString()+"  5.1");
			Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
			ArrayList<Integer> scopeArray = getScopeArray(node);
			Set<Node> methods=new HashSet<Node>();
			Set <Node> clist= new HashSet<Node>();
			printMethodsMap.put(node.toString(),node.getStartPosition());

			for(Node me : melist)
			{
				if(matchParams(me, node.arguments())==true)
					methods.add(me);
			}

			for(Node m : methods)
			{
				//System.out.println(m.getProperty("id"));
				Node fcname=model.getMethodContainer(m);
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), m);
				Node retElement = model.getMethodReturn(m);
				if(retElement!=null)
					globalmethods.put(node.toString(),retElement);
			}

			if(clist.isEmpty()==false)
			{
				HashMultimap<ArrayList<Integer>, Node> replacer = HashMultimap.create();
				replacer.putAll(scopeArray, clist);
				globaltypes2.put(e.toString(),replacer);
				if(printTypesMap.containsKey(e.toString())==false)
				{
					printTypesMap.put(e.toString(), e.getStartPosition());
				}
				//System.out.println("!!!:"+node.getExpression().getStartPosition());
				affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
				affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
				//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
				//printtypes.putAll(printTypesMap.get(e.toString()), clist);
				//change affected types of e.toString() too
				//System.out.println("1&&&"+clist);
				for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
				{
					printtypes.replaceValues(affectedNode, clist);
					//System.out.println("0");
				}

				for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
				{
					Collection<Node>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
					//System.out.println("1:"+affectedNode);
					printmethods.replaceValues(affectedNode, temp);
				}
				printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
				//System.out.println(affectedTypes);
			}
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
		else
			return false;
	}

	private boolean matchParams(Node me, List<ASTNode> params) 
	{

		ArrayList<HashSet<String>> nodeArgs = new ArrayList<HashSet<String>>();
		TreeSet<Node>graphNodes = new TreeSet<Node>(new Comparator<Node>()
				{
			public int compare(Node a, Node b)
			{
				return (Integer)a.getProperty("paramIndex")-(Integer)b.getProperty("paramIndex");
			}
				});
		graphNodes = model.getMethodParams(me);
		//System.out.println(params + " : " + me.getProperty("id") + " : "+ graphNodes.size());
		if(graphNodes.size() != params.size())
			return false;
		if(params.size()==0 && graphNodes.size()==0)
		{
			return true;
		}
		for(ASTNode param : params)
		{
			HashSet<String> possibleTypes = new HashSet<String>();
			if(param.getNodeType()==34)
			{
				possibleTypes.add("int");
				possibleTypes.add("byte");
				possibleTypes.add("float");
				possibleTypes.add("double");
				possibleTypes.add("long");
				possibleTypes.add("short");
			}
			else if(param.getNodeType()==9)
			{
				possibleTypes.add("boolean");
			}
			else if(param.getNodeType()==13)
			{
				possibleTypes.add("char");
			}
			else if(param.getNodeType()==27)
			{
				InfixExpression tempNode = (InfixExpression) param;
				if(tempNode.getLeftOperand().getNodeType() == 45 || tempNode.getRightOperand().getNodeType() == 45)
					possibleTypes.add("String");
				else if(tempNode.getLeftOperand().getNodeType() == 34 || tempNode.getRightOperand().getNodeType() == 34)
				{
					possibleTypes.add("int");
					possibleTypes.add("byte");
					possibleTypes.add("float");
					possibleTypes.add("double");
					possibleTypes.add("long");
					possibleTypes.add("short");
				}
				else
					possibleTypes.add("UNKNOWN");
			}
			else if(param.getNodeType()==45)
			{
				possibleTypes.add("String");
			}
			else if (param.getNodeType()==42)
			{
				if(globaltypes2.containsKey(param.toString()))
				{
					HashMultimap<ArrayList<Integer>, Node> celist_temp = globaltypes2.get(param.toString());
					ArrayList<Integer> intermediate = getNodeSet(celist_temp, getScopeArray(param));
					if(intermediate!=null)
					{
						Set<Node> localTypes = celist_temp.get(intermediate);
						for(Node localType : localTypes)
						{
							possibleTypes.add((String) localType.getProperty("id"));
						}
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==32)
			{
				if(globalmethods.containsKey(param.toString()))
				{
					Set<Node> localTypes = globalmethods.get(param.toString());
					for(Node localType : localTypes)
					{
						possibleTypes.add((String) localType.getProperty("id"));
					}
				}
				else
				{
					possibleTypes.add("UNKNOWN");
					//System.out.println("UNKNOWN");
				}
			}
			else if(param.getNodeType()==14)
			{
				ClassInstanceCreation tempNode = (ClassInstanceCreation) param;
				possibleTypes.add(tempNode.getType().toString());
				//System.out.println("14:  "+tempNode.getType().toString());
			}
			else
			{
				possibleTypes.add("UNKNOWN");
			}
			nodeArgs.add(possibleTypes);
		}
		Iterator<Node> iter1 = graphNodes.iterator();
		Iterator<HashSet<String>> iter2 = nodeArgs.iterator();
		while(iter1.hasNext())
		{
			Node graphParam = iter1.next();
			HashSet<String> args = iter2.next();
			int flag=0;
			for(String arg : args)
			{
				if(graphParam.getProperty("exactName").equals(arg)== true || graphParam.getProperty("id").equals(arg)==true)
				{
					flag=0;
					break;
				}
				else if(arg.equals("UNKNOWN"))
				{
					flag=0;
					break;
				}
				else if(model.checkIfParentNode(graphParam, arg))
				{
					flag=0;
					break;
				}
				else
					flag=1;
			}
			if(flag==1)
				return false;
		}
		return true;
	}

	private Collection<Node> getReplacementClassList(Set<Node> set,	Set<Node> clist) 
	{
		Collection<Node> returnSet=new HashSet<Node>();
		for(Node me: set)
		{
			String cname=(String) model.getMethodContainer(me).getProperty("id");
			int flag=0;
			for(Node ce:clist)
			{
				if(((String)ce.getProperty("id")).equals(cname))
				{
					flag=1;
				}
				else
				{
				}
			}
			if(flag==1)
				returnSet.add(me);
		}
		return returnSet;
	}

	public boolean visit(TypeDeclaration node)
	{
		classname=node.getName().toString();
		if(node.getSuperclassType()!=null)
		{
			if(node.getSuperclassType().getNodeType()==74)
			{
				superclassname=((ParameterizedType)node.getSuperclassType()).getType().toString();
			}
			else
			{
				superclassname=node.getSuperclassType().toString();
			}
		}
		for(Object ob:node.superInterfaceTypes())
		{	
			interfaces.add(ob);
		}
		return true;
	}

	public boolean visit(MethodDeclaration node)
	{
		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> param=node.parameters();
		for(int i=0;i<param.size();i++)
		{
			ArrayList<Integer> scopeArray = getScopeArray(node);
			HashMultimap<ArrayList<Integer>,Node> temp = null;
			if(globaltypes2.containsKey(param.get(i).getName().toString()))
			{
				temp = globaltypes2.get(param.get(i).getName().toString());
			}
			else
			{
				temp = HashMultimap.create();
			}
			Collection<Node> ce=model.getCandidateClassNodes(param.get(i).getType().toString());
			ce = getNewCeList(ce);
			for(Node c : ce)
			{
				temp.put(scopeArray, c);
				if(ce.size() < tolerance)
				{
					String possibleImport = checkAndSlice(c.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(param.get(i).getType().getStartPosition(),c);
				printTypesMap.put(param.get(i).getName().toString(), param.get(i).getType().getStartPosition());
			}
			globaltypes2.put(param.get(i).getName().toString(), temp);
		}
		if(superclassname!=null)
		{
			Collection<Node> ce=model.getCandidateClassNodes(superclassname);
			ce = getNewCeList(ce);
			for(Node c : ce)
			{
				Collection<Node> methods=model.getMethodNodes(c);
				for(Node me : methods)
				{
					if(((String)me.getProperty("exactName")).equals(node.getName()))
					{
						if(c!=null && me!=null)
						{
							if(matchParams(me, node.parameters())==true)
							{
								Node parentNode = model.getMethodContainer(me);
								if(methods.size() < tolerance)
								{
									String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
									if(possibleImport!=null)
									{
										importList.add(possibleImport);
									}
								}
								printtypes.put(node.getStartPosition(),parentNode);
								printmethods.put(node.getStartPosition(), me);
							}
						}	
					}
				}
			}
		}
		if(!interfaces.isEmpty())
		{
			for(int i=0;i<interfaces.size();i++)
			{
				Collection<Node> ce=model.getCandidateClassNodes(interfaces.get(i).toString());
				ce = getNewCeList(ce);
				for(Node c : ce)
				{
					Collection<Node> methods=model.getMethodNodes(c);
					for(Node me : methods)
					{
						if(((String)me.getProperty("exactName")).equals(node.getName()))
						{
							if(c!=null && me!=null)
							{
								if(matchParams(me, node.parameters())==true)
								{
									Node parentNode = model.getMethodContainer(me);
									if(methods.size() < tolerance)
									{
										String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
										if(possibleImport!=null)
										{
											importList.add(possibleImport);
										}
									}
									printtypes.put(node.getStartPosition(),parentNode);
									printmethods.put(node.getStartPosition(), me);
								}
							}	
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ConstructorInvocation node)
	{	
		Collection<Node> celist=model.getCandidateClassNodes(classname);
		celist = getNewCeList(celist);
		for(Node ce : celist)
		{
			Collection<Node>melist=model.getMethodNodes(ce);
			for(Node me:melist)
			{
				if(((String)me.getProperty("exactName")).equals("<init>"))
				{
					if(matchParams(me, node.arguments())==true)
					{
						printmethods.put(node.getStartPosition(), me);
						Node parentNode = model.getMethodContainer(me);
						if(melist.size() < tolerance)
						{
							String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(node.getStartPosition(),parentNode);
						if(model.getMethodReturn(me)!=null)
							globalmethods.put(node.toString(), model.getMethodReturn(me));

					}
				}
			}
		}
	}

	public boolean visit(CatchClause node)
	{
		ArrayList<Integer> scopeArray = getScopeArray(node);
		HashMultimap<ArrayList<Integer>, Node> temp = null;
		if(globaltypes2.containsKey(node.getException().getName().toString()))
		{
			temp = globaltypes2.get(node.getException().getName().toString());
		}
		else
		{
			temp = HashMultimap.create();
		}
		Collection<Node> celist=model.getCandidateClassNodes(node.getException().getType().toString());
		celist = getNewCeList(celist);
		for(Node ce : celist)
		{
			temp.put(scopeArray, ce);
			if(celist.size() < tolerance)
			{
				String possibleImport = checkAndSlice(ce.getProperty("id").toString());
				if(possibleImport!=null)
				{
					importList.add(possibleImport);
				}
			}
			printtypes.put(node.getException().getType().getStartPosition(), ce);
			printTypesMap.put(node.getException().getName().toString(), node.getException().getType().getStartPosition());
		}
		globaltypes2.put(node.getException().getName().toString(), temp);
		return true;
	}

	public void endVisit(SuperConstructorInvocation node)
	{	
		Collection<Node> celist=model.getCandidateClassNodes(superclassname);
		celist = getNewCeList(celist);
		for(Node ce : celist)
		{
			Collection<Node>melist=model.getMethodNodes(ce);
			for(Node me:melist)
			{
				if(((String)me.getProperty("exactName")).equals("<init>"))
				{
					if(matchParams(me, node.arguments())==true)
					{
						printmethods.put(node.getStartPosition(),me);
						Node parentNode = model.getMethodContainer(me);
						if(melist.size() < tolerance)
						{
							String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
							if(possibleImport!=null)
							{
								importList.add(possibleImport);
							}
						}
						printtypes.put(node.getStartPosition(), parentNode);
						if(model.getMethodReturn(me)!=null)
							globalmethods.put(node.toString(),model.getMethodReturn(me));
					}
				}	
			}
		}
	}

	public void endVisit(SuperMethodInvocation node)
	{
		Collection<Node> celist=model.getCandidateClassNodes(superclassname);
		celist = getNewCeList(celist);
		Set<Node> methods=new TreeSet<Node>();
		Set<Node> tempmethods1=new TreeSet<Node>();
		Set<Node> tempmethods2=new TreeSet<Node>();
		Set <Node> clist= new HashSet<Node>();
		printMethodsMap.put(node.toString(),node.getStartPosition());
		for(Node ce : celist)
		{
			Collection<Node> melist=model.getMethodNodes(ce);
			for(Node me : melist)
			{
				if(node.getName().toString().equals((String)me.getProperty("exactName")))
				{
					if(matchParams(me, node.arguments())==true)
					{
						methods.add(me);
					}
					//System.out.println("4:"+me.getId()+":"+c.getId());
				}
			}
			for(Node me : methods)
			{
				Node fcname=model.getMethodContainer(me);
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), me);
				if(model.getMethodReturn(me)!=null)
					globalmethods.put(node.toString(),model.getMethodReturn(me));
			}
			if(clist.isEmpty()==false)
			{
				//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
				//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
				//change affected types of e.toString() too
				printtypes.replaceValues(node.getStartPosition(), clist);
			}
		}
		if(tempmethods1.isEmpty() && tempmethods2.isEmpty())
		{
			Collection<Node> melist=model.getCandidateMethodNodes(node.getName().toString());
			for(Node me : melist)
			{
				if(matchParams(me, node.arguments())==true)
				{
					printmethods.put(node.getName().getStartPosition(), me);
					Node parentNode = model.getMethodContainer(me);
					if(melist.size() < tolerance)
					{
						String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
						if(possibleImport!=null)
						{
							importList.add(possibleImport);
						}
					}
					printtypes.put(node.getName().getStartPosition(),parentNode);
					if(model.getMethodReturn(me)!=null)
						globalmethods.put(node.toString(), model.getMethodReturn(me));
				}
			}
		}
	}

	public boolean visit(final ClassInstanceCreation node)
	{
		ASTNode anon=node.getAnonymousClassDeclaration();
		if(anon!=null)
		{
			//MyNewASTVisitor visitor_new=new MyNewASTVisitor(db,cu,cutype);
			anon.accept(new ASTVisitor(){
				public void endVisit(MethodDeclaration md)
				{
					//System.out.println("anon:" + md.getName().toString());
					Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
					celist = getNewCeList(celist);
					for(Node ce : celist)
					{
						Collection<Node>melist=model.getMethodNodes(ce);
						for(Node me:melist)
						{

							if(((String)me.getProperty("exactName")).equals(md.getName().toString()))
							{
								if(matchParams(me, md.parameters())==true)
								{
									printmethods.put(md.getStartPosition(),me);
									Node parentNode = model.getMethodContainer(me);
									if(melist.size() < tolerance)
									{
										String possibleImport = checkAndSlice(parentNode.getProperty("id").toString());
										if(possibleImport!=null)
										{
											importList.add(possibleImport);
										}
									}
									printtypes.put(md.getStartPosition(), model.getMethodContainer(me));
								}
							}
						}
					}
					//return true;
				}
			});
		}
		else
		{
			Collection<Node> celist=model.getCandidateClassNodes(node.getType().toString());
			celist = getNewCeList(celist);
			for(Node ce:celist)
			{
				Collection<Node> melist=model.getMethodNodes(ce);
				for(Node me:melist)
				{
					if(((String)me.getProperty("exactName")).equals("<init>"))
					{
						if(matchParams(me, node.arguments())==true)
						{
							//System.out.println("##########"+node.getParent().getParent().getStartPosition()+node.getType().getStartPosition());
							printmethods.put(node.getType().getStartPosition(),me);
							affectedMethods.put(node.getParent().getParent().getStartPosition(), node.getType().getStartPosition());
							//printtypes.put(node.getType().getStartPosition(), model.getClassElementForMethod(me.getId()));
							//System.out.println("class inst:"+node.getType()+":"+ImpreciseModel.getClassForMethod(me));
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ClassInstanceCreation node)
	{	
		//System.out.println(node.getType().toString()+"0000"+node.toString()+"0000"+node.getParent().getParent());
		Collection<Node> ce=model.getCandidateClassNodes(node.getType().toString());
		
		
		ce = getNewCeList(ce);
		for(Node c : ce)
		{
			//globaltypes.put(node.toString(), c);
			//affectedTypes.put(node.getExpression().getStartPosition(), node.get.getStartPosition());
			//printtypes.put(node.getType().getStartPosition(), c);
			printTypesMap.put(node.toString(), node.getType().getStartPosition());
			globalmethods.put(node.toString(), c);
			//System.out.println(node.toString() + " - " + c);
		}
	}

	public boolean visit(CastExpression node)
	{
		Collection<Node> ce=model.getCandidateClassNodes(node.getType().toString());
		ce = getNewCeList(ce);
		HashMultimap<ArrayList<Integer>, Node> temp1= null;
		HashMultimap<ArrayList<Integer>, Node> temp2= null;
		ArrayList<Integer> scopeArray = getScopeArray(node);
		if(globaltypes2.containsKey(node.toString()))
		{
			temp1 = globaltypes2.get(node.toString());
		}
		else
		{
			temp1 = HashMultimap.create();
		}
		if(globaltypes2.containsKey("("+node.toString()+")"))
		{
			temp2 = globaltypes2.get("("+node.toString()+")");
		}
		else
		{
			temp2 = HashMultimap.create();
		}
		for(Node c : ce)
		{
			if(c!=null)
			{
				temp1.put(scopeArray, c);
				if(ce.size() < tolerance)
				{
					String possibleImport = checkAndSlice(c.getProperty("id").toString());
					if(possibleImport!=null)
					{
						importList.add(possibleImport);
					}
				}
				printtypes.put(node.getType().getStartPosition(), c);
				temp2.put(scopeArray, c);
			}
		}
		globaltypes2.put(node.toString(), temp1);
		globaltypes2.put("("+node.toString()+")", temp2);
		return true;
	}

	public void endVisit(Assignment node)
	{
		String lhs,rhs;
		lhs=node.getLeftHandSide().toString();
		rhs=node.getRightHandSide().toString();
		//System.out.println(lhs + " : " + rhs);
		if(globalmethods.containsKey(rhs))
		{
			//System.out.println("yes! :" + rhs);
			if(!globaltypes2.containsKey(lhs))
			{
				globalmethods.putAll(lhs, globalmethods.get(rhs));
				globalmethods.putAll(lhs, globalmethods.get(rhs));
				
			}
			else
			{	
				//System.out.println(lhs);
				int flag=0;
				//Set<Node> temp=new HashSet<Node>();
				Set<Node> temp = new HashSet<Node>();
				HashMultimap<ArrayList<Integer>, Node> celist_temp = globaltypes2.get(lhs);
				ArrayList<Integer> scopeArray = getNodeSet(celist_temp, getScopeArray(node));
				if(scopeArray!=null)
				{
					Set<Node> localTypes = celist_temp.get(scopeArray);
					for(Node ce : localTypes)
					{
						if(globalmethods.get(rhs).contains((String)ce.getProperty("id")))
						{
							flag=1;
							temp.add(ce);
						}
					}
				}
				if(flag==1)
				{
					//System.out.println("^^^^^^^^^:"+temp+node.getStartPosition());
					globaltypes2.get(lhs).replaceValues(scopeArray,temp);
				}

			}
		}
	}

	public boolean visit(ImportDeclaration node)
	{

		String importStatement = node.getName().getFullyQualifiedName();
		if(importStatement.endsWith(".*"))
		{
			importStatement= importStatement.substring(0, importStatement.length()-2);
		}
		importList.add(importStatement);
		//System.out.println(importStatement);
		return true;
	}

	public JSONObject printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		//Collections.sort(printtypes, printtypes.keySet());
		for(Integer key:printtypes.keySet())
		{
			int flag=0;
			String cname=null;
			List<String> namelist = new ArrayList<String>();
			for(Node type_name:printtypes.get(key))
			{
				int isprimitive=0;
				for(String primitive_type : primitive)
				{
					if(((String)type_name.getProperty("id")).equals(primitive_type)==true)
					{
						isprimitive=1;
						break;
					}
				}
				if(isprimitive==0)
				{
					String nameOfClass = (String)type_name.getProperty("id");
					/*if(nameOfClass.indexOf("com.google.appengine.repackaged.")!=-1 || 
							nameOfClass.indexOf("com.ning.metrics.serialization")!=-1 || 
									nameOfClass.indexOf("com.ning.metrics.eventtracker")!=-1 || 
											nameOfClass.indexOf("jruby.joda.")!=-1 || 
													nameOfClass.indexOf("org.elasticsearch.common")!=-1 || 
															nameOfClass.indexOf("com.proofpoint.hive")!=-1 || 
																	nameOfClass.indexOf("clover.cenqua_com_licensing")!=-1 ||
																			nameOfClass.indexOf("de.huxhorn.sulky.stax")!=-1 ||
																					nameOfClass.indexOf("org.osjava.reportrunner")!=-1 ||
																						nameOfClass.indexOf("com.ning.metrics.eventtracker")!=-1)*/
					/*if(nameOfClass.indexOf("br.com.caelum.vraptor")!=-1 || 
						nameOfClass.indexOf("com.cedarsoft")!=-1 || 
						nameOfClass.indexOf("com.cloudbees")!=-1 || 
						nameOfClass.indexOf("com.ovea.jetty")!=-1 || 
						nameOfClass.indexOf("com.wwm.attrs.internal")!=-1 || 
						nameOfClass.indexOf("cucumber.runtime")!=-1 || 
						nameOfClass.indexOf("edu.internet2")!=-1 ||
						nameOfClass.indexOf("hudson.plugins")!=-1 ||
						nameOfClass.indexOf("net.incongru.taskman")!=-1 ||
						nameOfClass.indexOf("no.antares")!=-1 ||
						nameOfClass.indexOf("org.activemq")!=-1 ||
						nameOfClass.indexOf("org.apache")!=-1 ||
						nameOfClass.indexOf("org.codehaus")!=-1 ||
						nameOfClass.indexOf("org.fabric3")!=-1 ||
						nameOfClass.indexOf("org.jasig")!=-1 ||
						nameOfClass.indexOf("org.kohsuke")!=-1 ||
						nameOfClass.indexOf("org.kuali")!=-1 ||
						nameOfClass.indexOf("org.mattressframework")!=-1 ||
						nameOfClass.indexOf("org.nakedobjects")!=-1 ||
						nameOfClass.indexOf("org.pitest")!=-1 ||
						nameOfClass.indexOf("org.sca4j")!=-1 ||
						nameOfClass.indexOf("org.sonatype")!=-1 ||
						nameOfClass.indexOf("org.springframework")!=-1 ||
						nameOfClass.indexOf("org.jboss")!=-1 ||
						nameOfClass.indexOf("org.compass")!=-1 ||
						nameOfClass.indexOf("org.jibx")!=-1 ||
						nameOfClass.indexOf("org.dom4j")!=-1 ||
						nameOfClass.indexOf("com.quigley")!=-1 ||
						nameOfClass.indexOf("org.compass")!=-1 ||
						nameOfClass.indexOf("de.javakaffee")!=-1)
						{
						System.out.println("came hetre");
						}
					else*/
						namelist.add("\""+nameOfClass+"\"");
					if(flag==0)
					{
						cname=(String) type_name.getProperty("exactName");
						flag=1;
					}
				}

			}
			if(namelist.isEmpty()==false)
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
		for(Integer key:printmethods.keySet())
		{
			List<String> namelist = new ArrayList<String>();
			String mname=null;
			for(Node method_name:printmethods.get(key))
			{
				String nameOfMethod = (String)method_name.getProperty("id");
				/*if(nameOfMethod.indexOf("com.google.appengine.repackaged.")!=-1 || 
						nameOfMethod.indexOf("com.ning.metrics.serialization")!=-1 || 
								nameOfMethod.indexOf("com.ning.metrics.eventtracker")!=-1 || 
										nameOfMethod.indexOf("jruby.joda.")!=-1 || 
												nameOfMethod.indexOf("org.elasticsearch.common")!=-1 || 
														nameOfMethod.indexOf("com.proofpoint.hive")!=-1 || 
																nameOfMethod.indexOf("clover.cenqua_com_licensing")!=-1 ||
																		nameOfMethod.indexOf("de.huxhorn.sulky.stax")!=-1 ||
																				nameOfMethod.indexOf("org.osjava.reportrunner")!=-1 ||
																					nameOfMethod.indexOf("com.ning.metrics.eventtracker")!=-1)*/
				String nameOfClass = nameOfMethod;
				/*if(nameOfClass.indexOf("br.com.caelum.vraptor")!=-1 || 
						nameOfClass.indexOf("com.cedarsoft")!=-1 || 
						nameOfClass.indexOf("com.cloudbees")!=-1 || 
						nameOfClass.indexOf("com.ovea.jetty")!=-1 || 
						nameOfClass.indexOf("com.wwm.attrs.internal")!=-1 || 
						nameOfClass.indexOf("cucumber.runtime")!=-1 || 
						nameOfClass.indexOf("edu.internet2")!=-1 ||
						nameOfClass.indexOf("hudson.plugins")!=-1 ||
						nameOfClass.indexOf("net.incongru.taskman")!=-1 ||
						nameOfClass.indexOf("no.antares")!=-1 ||
						nameOfClass.indexOf("org.activemq")!=-1 ||
						nameOfClass.indexOf("org.apache")!=-1 ||
						nameOfClass.indexOf("org.codehaus")!=-1 ||
						nameOfClass.indexOf("org.fabric3")!=-1 ||
						nameOfClass.indexOf("org.jasig")!=-1 ||
						nameOfClass.indexOf("org.kohsuke")!=-1 ||
						nameOfClass.indexOf("org.kuali")!=-1 ||
						nameOfClass.indexOf("org.mattressframework")!=-1 ||
						nameOfClass.indexOf("org.nakedobjects")!=-1 ||
						nameOfClass.indexOf("org.pitest")!=-1 ||
						nameOfClass.indexOf("org.sca4j")!=-1 ||
						nameOfClass.indexOf("org.sonatype")!=-1 ||
						nameOfClass.indexOf("org.springframework")!=-1 ||
						nameOfClass.indexOf("org.jboss")!=-1 ||
						nameOfClass.indexOf("org.compass")!=-1 ||
						nameOfClass.indexOf("org.jibx")!=-1 ||
						nameOfClass.indexOf("org.dom4j")!=-1 ||
						nameOfClass.indexOf("com.quigley")!=-1 ||
						nameOfClass.indexOf("org.compass")!=-1 ||
						nameOfClass.indexOf("de.javakaffee")!=-1)	
				{}
				else*/
					namelist.add("\""+nameOfMethod+"\"");
					//namelist.add("\""+method_name.getProperty("id")+"\"");
					mname=(String) method_name.getProperty("exactName");
			}
			if(namelist.isEmpty()==false)
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
			//System.out.println(main_json.toString());
		}
		if(main_json.isNull("api_elements"))
		{
			//System.out.println("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ); 
			//return("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" );
			return null;
		}
		else
		{
			//System.out.println(main_json.toString(3));
			return(main_json);
		}
		//printFields();
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