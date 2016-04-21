import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.ASTVisitor;


import com.google.common.collect.HashMultimap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
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

import ca.uwaterloo.cs.se.inconsistency.core.model2.ClassElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.MethodElement;
import ca.uwaterloo.cs.se.inconsistency.core.model2.so.ImpreciseModel;

class MyASTVisitor extends ASTVisitor{
	
	private ImpreciseModel model;
	private CompilationUnit cu;
	private int cutype;
	private HashMultimap<String, ClassElement> globalmethods=HashMultimap.create();//holds method return types for chains
	private HashMultimap<String, ClassElement> globaltypes=HashMultimap.create();//holds variables, fields and method param types
	private HashMultimap<Integer, ClassElement> printtypes=HashMultimap.create();//holds node start loc and possible types
	private HashMultimap<Integer, MethodElement> printmethods=HashMultimap.create();//holds node start posns and possible methods they can be
	private HashMap<String, Integer> printTypesMap=new HashMap<String, Integer>();//maps node start loc to variable names
	private HashMap<String, Integer> printMethodsMap=new HashMap<String, Integer>();//holds node start locs with method names
	private HashMultimap<Integer, Integer> affectedTypes = HashMultimap.create();//holds node start locs with list of start locs they influence
	private HashMultimap<Integer, Integer> affectedMethods = HashMultimap.create();//holds node start locs with list of start locs they influence

	private String classname = null;
	private String superclassname=null;
	private ArrayList<Object> interfaces=new ArrayList<Object>();
	

	
	public MyASTVisitor(ImpreciseModel m, CompilationUnit cu, int cutype) 
	{
		this.model=m;
		this.cu=cu;
		this.cutype=cutype;
	}
	
	public void printFields()
	{
		System.out.println("globalmethods"+globalmethods);
		System.out.println("globaltypes"+globaltypes);
		System.out.println("printtypes"+printtypes);
		System.out.println("printmethods"+printmethods);
		System.out.println("printTypesMap"+printTypesMap);
		System.out.println("printMethodsMap"+printMethodsMap);
		System.out.println("affectedTypes"+affectedTypes);
		System.out.println("affectedMethods"+affectedMethods);
	}
	
	public void endVisit(VariableDeclarationStatement node)
	{
		for(int j=0;j<node.fragments().size();j++)
		{
			Collection<ClassElement> celist=model.getCandidateClasses(node.getType().toString());
			for(ClassElement ce : celist)
			{
				if(ce!=null)
				{
					System.out.println("!!!!"+ce.getId());
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), ce);
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
		}
	}
	
	public void endVisit(FieldDeclaration node) 
	{
		for(int j=0;j<node.fragments().size();j++)
		{	
			Collection<ClassElement> types=new HashSet<ClassElement>();	
			if(node.getType().getNodeType()==74)
			{	Collection<ClassElement> celist=model.getCandidateClasses(((ParameterizedType)node.getType()).getType().toString());
			for(ClassElement ce : celist)
			{
				if(ce!=null)
				{
					types.add(ce);
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(),ce);
					printtypes.put(node.getType().getStartPosition(), ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			}
			else
			{	
			Collection<ClassElement> celist=model.getCandidateClasses(node.getType().toString());
			for(ClassElement ce : celist)
			{
				if(ce!=null){
					types.add(ce);
					globaltypes.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), ce);
					printtypes.put(node.getType().getStartPosition(),ce);
					printTypesMap.put(((VariableDeclarationFragment)node.fragments().get(j)).getName().toString(), node.getType().getStartPosition());
				}
			}
			}
		}
	}

	public void endVisit(MethodInvocation node)
	{
		//System.out.println("###"+node.getName().toString());
		Expression e=node.getExpression();
		if(e==null)
		{
			if(superclassname!=null)
			{	
				/*
				 * Handles inheritance, where methods from superclasses can be directly called
				 * 
				 */
				Collection<ClassElement> celist=model.getCandidateClasses(superclassname);
				for(ClassElement ce : celist)
				{
					Collection<MethodElement> melist=ce.getMethods();
					for(MethodElement me : melist)
					{
						if(me.getExactName().toString().equals(node.getName().toString()))
						{
							if(me.getParameters().size()==node.arguments().size())
							{
								printtypes.put(node.getStartPosition(),model.getClassElementForMethod(me.getId()));
								printmethods.put(node.getStartPosition(), me);
								if(me.getReturnElement().getType()!=null)
									globalmethods.put(node.getName().toString(),me.getReturnElement().getType());
							}	
						}
					}
				}
			}
			else
			{
				/*
				 * Might be user declared helper functions or maybe object reference is assumed to be obv in the snippet
				 */
				Collection<MethodElement> melist=model.getCandidateMethods(node.getName().toString());
				for(MethodElement me : melist)
				{
					if(me.getParameters().size()==node.arguments().size())
					{
						printtypes.put(node.getName().getStartPosition(), model.getClassElementForMethod(me.getId()));
						printmethods.put(node.getName().getStartPosition(), me);
						if(me.getReturnElement().getType()!=null)
							globalmethods.put(node.toString(),me.getReturnElement().getType());
					}
				}
			}
		}
		else if(e.toString().contains("System."))
		{
			
		}
		else if(globaltypes.containsKey(e.toString()))
		{
			String exactname=null;
			Set<MethodElement> methods=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods1=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods2=new HashSet<MethodElement>();
			Set <ClassElement> clist= new HashSet<ClassElement>();
			Set <ClassElement> celist=globaltypes.get(e.toString());
			printMethodsMap.put(node.toString(),node.getStartPosition());
			affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(ClassElement ce:celist)
			{
					exactname=ce.getExactName();
					Collection<MethodElement> melist = ce.getMethods();
					for(MethodElement me : melist)
					{
						if(me!=null && me.getExactName().equals(node.getName().toString()) && node.arguments().size()==me.getParameters().size())
						{
								if(model.getClassElementForMethod(me.getId()).getId().equals(ce.getId()))
								{
									tempmethods1.add(me);
								}
								else
								{
									tempmethods2.add(me);
								}
						}
					}
					if(tempmethods1.isEmpty()!=true)
					{
						methods=tempmethods1;
					}
					else
					{
						methods=tempmethods2;
					}
					for(MethodElement m : methods)
					{
							printmethods.put(node.getName().getStartPosition(), m);
							ClassElement fcname=model.getClassElementForMethod(m.getId());
							if(fcname!=null && fcname.getExactName().equals(exactname)==true)
								clist.add(fcname);
							if(m.getReturnElement().toString()!=null)
								globalmethods.put(node.toString(), m.getReturnElement().getType());
					}
			}
			//clist has the list of class names extracted from the methodname that match the exactname
			//use that to replace previously assigned values
			if(clist.isEmpty()==false)
			{
					globaltypes.replaceValues(e.toString(),clist);
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
						Collection<MethodElement>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
					
			}
		}
		else if(e.toString().matches("[A-Z][a-zA-Z]*"))
		{
			String exactname="";
			Collection<ClassElement> celist=model.getCandidateClasses(e.toString());
			Set<MethodElement> methods=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods1=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods2=new HashSet<MethodElement>();
			Set <ClassElement> clist= new HashSet<ClassElement>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			//affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			//affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(ClassElement ce : celist)
			{
				exactname=ce.getExactName();
				Collection<MethodElement> melist = ce.getMethods();
				for(MethodElement me : melist)
				{
					if(me.getExactName().equals(node.getName().toString()) && me!=null && me.getParameters().size()==node.arguments().size())
					{	
							if(model.getClassElementForMethod(me.getId()).getId().equals(ce.getId()))
							{
								tempmethods1.add(me);
							}
							else
							{
								tempmethods2.add(me);
							}
					}
				}	
				if(tempmethods1.isEmpty()!=true)
				{
					methods=tempmethods1;
				}
				else
				{
					methods=tempmethods2;
				}
				for(MethodElement m : methods)
				{
					printmethods.put(node.getName().getStartPosition(), m);
					ClassElement fcname=model.getClassElementForMethod(m.getId());
					if(fcname!=null && fcname.getExactName().equals(exactname)==true)
						clist.add(fcname);
					if(m.getReturnElement().toString()!=null)
						globalmethods.put(node.toString(),m.getReturnElement().getType());
				}
			}
			
			
			
			if(clist.isEmpty()==false)
			{
				/*
					//globaltypes.replaceValues(e.toString(),clist);
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<MethodElement>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
				*/
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
					
			}
		}
		else if(globalmethods.containsKey(e.toString()))
		{
			String exactname="";
			Set<ClassElement> celist=globalmethods.get(e.toString());
			Set<MethodElement> methods=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods1=new HashSet<MethodElement>();
			Set<MethodElement> tempmethods2=new HashSet<MethodElement>();
			Set <ClassElement> clist= new HashSet<ClassElement>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			//affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
			//affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			for(ClassElement ce : celist)
			{
				exactname=ce.getExactName();
				Collection<MethodElement> melist = ce.getMethods();
				for(MethodElement me : melist)
				{
					if(me.getExactName().equals(node.getName().toString()) && me!=null && me.getParameters().size()==node.arguments().size())
					{	
							if(model.getClassElementForMethod(me.getId()).getId().equals(ce.getId()))
							{
								tempmethods1.add(me);
							}
							else
							{
								tempmethods2.add(me);
							}
					}
				}	
				if(tempmethods1.isEmpty()!=true)
				{
					methods=tempmethods1;
				}
				else
				{
					methods=tempmethods2;
				}
				for(MethodElement m : methods)
				{
					printmethods.put(node.getName().getStartPosition(), m);
					ClassElement fcname=model.getClassElementForMethod(m.getId());
					if(fcname!=null && fcname.getExactName().equals(exactname)==true)
						clist.add(fcname);
					if(m.getReturnElement().toString()!=null)
						globalmethods.put(node.toString(),m.getReturnElement().getType());
				}
			}
			
			if(clist.isEmpty()==false)
			{
					globaltypes.replaceValues(e.toString(),clist);
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					System.out.println("1&&&"+clist);
					for(Integer affectedNode:affectedTypes.get(printTypesMap.get(e.toString())))
					{
						printtypes.replaceValues(affectedNode, clist);
						//System.out.println("0");
					}
					
					for(Integer affectedNode:affectedMethods.get(printTypesMap.get(e.toString())))
					{
						Collection<MethodElement>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
		else
		{
			Collection<MethodElement> melist=model.getCandidateMethods(node.getName().toString());
			Set<MethodElement> methods=new HashSet<MethodElement>();
			Set <ClassElement> clist= new HashSet<ClassElement>();
			printMethodsMap.put(node.toString(),node.getStartPosition());
			if(printTypesMap.containsKey(e.toString())==true)
			{
				affectedTypes.put(printTypesMap.get(e.toString()), node.getExpression().getStartPosition());
				affectedMethods.put(printTypesMap.get(e.toString()), node.getName().getStartPosition());
			}
			for(MethodElement me : melist)
			{
				if(me.getExactName().equals(node.getName().toString()) && me!=null && me.getParameters().size()==node.arguments().size())
				{	
					methods.add(me);
				}
			}
			
			for(MethodElement m : methods)
			{
				ClassElement fcname=model.getClassElementForMethod(m.getId());
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), m);
				if(m.getReturnElement().toString()!=null)
					globalmethods.put(node.toString(),m.getReturnElement().getType());
			}

			if(clist.isEmpty()==false)
			{
					globaltypes.replaceValues(e.toString(),clist);
					if(printTypesMap.containsKey(e.toString())==false)
						printTypesMap.put(e.toString(), e.getStartPosition());
						
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
						Collection<MethodElement>temp=getReplacementClassList(printmethods.get(affectedNode),clist);
						//System.out.println("1:"+affectedNode);
							printmethods.replaceValues(affectedNode, temp);
					}
					printtypes.replaceValues(node.getExpression().getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
	}

	private Collection<MethodElement> getReplacementClassList(Set<MethodElement> set,	Set<ClassElement> clist) {
		Collection<MethodElement> returnSet=new HashSet<MethodElement>();
		for(MethodElement me: set)
		{
			String cname=me.getClassName();
			int flag=0;
			for(ClassElement ce:clist)
			{
				if(ce.getId().equals(cname))
				{
					flag=1;
					//System.out.println("777777777777777"+me.getId());
				}
				else
				{
					//System.out.println("00000000000"+me.getId());
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
			Set<String> types=new TreeSet<String>();	
			Collection<ClassElement> ce=model.getCandidateClasses(param.get(i).getType().toString());
			for(ClassElement c : ce)
			{
				if(c!=null)
				{
					types.add(c.getId());
					globaltypes.put(param.get(i).getName().toString(),c);
					printtypes.put(param.get(i).getType().getStartPosition(),c);
					printTypesMap.put(param.get(i).getName().toString(), param.get(i).getType().getStartPosition());
				}
				if(ce.size()>1){
				}
			}
		}
		if(superclassname!=null)
		{
			Collection<ClassElement> ce=model.getCandidateClasses(superclassname);
			for(ClassElement c : ce)
			{
				Collection<MethodElement> methods=c.getMethods();
				for(MethodElement me : methods)
				{
					if(me.getExactName().equals(node.getName()))
					{
						if(c!=null && me!=null && me.getParameters().size()==node.parameters().size())
						{
							printtypes.put(node.getStartPosition(),model.getClassElementForMethod(me.getId()));
							printmethods.put(node.getStartPosition(), me);
						}	
					}
				}
			}
		}
		if(!interfaces.isEmpty())
		{
			for(int i=0;i<interfaces.size();i++)
			{
				Collection<ClassElement> ce=model.getCandidateClasses(interfaces.get(i).toString());
				for(ClassElement c : ce)
				{
					Collection<MethodElement> methods=c.getMethods();
					for(MethodElement me : methods)
					{
						if(me.getExactName().equals(node.getName()))
						{
							if(c!=null && me!=null && me.getParameters().size()==node.parameters().size())
							{
								printtypes.put(node.getStartPosition(),model.getClassElementForMethod(me.getId()));
								printmethods.put(node.getStartPosition(), me);
							}	
						}
					}
				}
			}
		}
		return true;
	}

	public void endVisit(ConstructorInvocation node)
	{	//System.out.println("constructor:"+classname+"<init>");
		Collection<ClassElement> celist=model.getCandidateClasses(classname);
		for(ClassElement ce : celist)
		{
			Collection<MethodElement>melist=ce.getMethods();
			for(MethodElement me:melist)
			{
			if(me.exactMethodName().equals("<init>") && me.getParameters().size()==node.arguments().size())
			{
					printmethods.put(node.getStartPosition(), me);
					printtypes.put(node.getStartPosition(),model.getClassElementForMethod(me.getId()));
					if(me.getReturnElement()!=null)
						globalmethods.put(node.toString(), me.getReturnElement().getType());
			}
			}
		}
	}

	public void endVisit(SuperConstructorInvocation node)
	{	
		Collection<ClassElement> celist=model.getCandidateClasses(superclassname);
		for(ClassElement ce : celist)
		{
			Collection<MethodElement>melist=ce.getMethods();
			for(MethodElement me:melist)
			{
				if(me.exactMethodName().equals("<init>") && me.getParameters().size()==node.arguments().size())
				{
					printmethods.put(node.getStartPosition(),me);
					printtypes.put(node.getStartPosition(), model.getClassElementForMethod(me.getId()));
					if(me.getReturnElement()!=null)
						globalmethods.put(node.toString(), me.getReturnElement().getType());
				}	
			}
		}
	}

	public void endVisit(SuperMethodInvocation node)
	{
		Collection<ClassElement> celist=model.getCandidateClasses(superclassname);
		Set<MethodElement> methods=new TreeSet<MethodElement>();
		Set<MethodElement> tempmethods1=new TreeSet<MethodElement>();
		Set<MethodElement> tempmethods2=new TreeSet<MethodElement>();
		Set <ClassElement> clist= new HashSet<ClassElement>();
		printMethodsMap.put(node.toString(),node.getStartPosition());
		for(ClassElement ce : celist)
		{
			Collection<MethodElement> melist=ce.getMethods();
			for(MethodElement me : melist)
			{
				if(node.getName().toString().equals(me.getExactName()) && me.getParameters().size()==node.arguments().size())
				{
						if(model.getClassElementForMethod(me.getId()).getId().equals(ce.getId()))
						{
							tempmethods1.add(me);
							//System.out.println("3:"+me.getId()+":"+c.getId());
						}
						else
						{
							tempmethods2.add(me);
							//System.out.println("4:"+me.getId()+":"+c.getId());
						}
				}
			}
			if(tempmethods1.isEmpty()!=true)
			{
				methods=tempmethods1;
			}
			else
			{
				methods=tempmethods2;
			}
			for(MethodElement me : methods)
			{
				ClassElement fcname=model.getClassElementForMethod(me.getId());
				if(fcname!=null)
					clist.add(fcname);
				printmethods.put(node.getName().getStartPosition(), me);
				if(me.getReturnElement().toString()!=null)
					globalmethods.put(node.toString(),me.getReturnElement().getType());
			}
			if(clist.isEmpty()==false)
			{
					//System.out.println("****"+e.toString()+":"+printTypesMap.get(e.toString())+":"+clist+":"+node.getName().toString()+":"+node.getStartPosition());
					//printtypes.replaceValues(printTypesMap.get(e.toString()), clist);
					//change affected types of e.toString() too
					//System.out.println("1&&&"+clist);
					printtypes.replaceValues(node.getStartPosition(), clist);
					//System.out.println(affectedTypes);
			}
		}
		if(tempmethods1.isEmpty() && tempmethods2.isEmpty())
		{
			//System.out.println("yoyo");
			Collection<MethodElement> melist=model.getCandidateMethods(node.getName().toString());
			int argsize=node.arguments().size();
			for(MethodElement me : melist)
			{
				if(me.getParameters().size()==argsize)
				{
					printmethods.put(node.getName().getStartPosition(), me);
					printtypes.put(node.getName().getStartPosition(),model.getClassElementForMethod(me.getId()));
					if(me.getReturnElement().getType()!=null)
						globalmethods.put(node.toString(), me.getReturnElement().getType());
				}
			}
		}
	}

	public boolean visit(final ClassInstanceCreation node)
	{
		ASTNode anon=node.getAnonymousClassDeclaration();
		if(anon!=null)
		{
			anon.accept(new ASTVisitor(){
				public boolean visit(MethodDeclaration md)
				{
					//System.out.println("anon:");
					Collection<ClassElement> celist=model.getCandidateClasses(node.getType().toString());
					for(ClassElement ce : celist)
					{
						Collection<MethodElement>melist=ce.getMethods();
						for(MethodElement me:melist)
						{
						if(me.exactMethodName().equals(md.getName().toString()) && md.parameters().size()==me.getParameters().size())
						{
								printmethods.put(md.getStartPosition(),me);
								printtypes.put(md.getStartPosition(), model.getClassElementForMethod(me.getId()));
						}
						}
					}
					return true;
				}
			});
		}
		else
		{
			Collection<ClassElement> celist=model.getCandidateClasses(node.getType().toString());
			for(ClassElement ce:celist)
			{
				Collection<MethodElement> melist=ce.getMethods();
				for(MethodElement me:melist)
				{
					if(me.exactMethodName().equals("<init>") && me.getParameters().size()==node.arguments().size())
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
		return true;
	}

	public void endVisit(ClassInstanceCreation node)
	{	
		//System.out.println(node.getType().toString()+"0000"+node.toString()+"0000"+node.getParent().getParent());
		Collection<ClassElement> ce=model.getCandidateClasses(node.getType().toString());
		for(ClassElement c : ce)
		{
			if(c!=null){
				//globaltypes.put(node.toString(), c);
				//affectedTypes.put(node.getExpression().getStartPosition(), node.getName().getStartPosition());
				//printtypes.put(node.getType().getStartPosition(), c);
				printTypesMap.put(node.toString(), node.getType().getStartPosition());
			}
		}
	}
	
	public boolean visit(CastExpression node)
	{
		Collection<ClassElement> ce=model.getCandidateClasses(node.getType().toString());
		for(ClassElement c : ce)
		{
			if(c!=null){
				globaltypes.put(node.toString(), c);
				printtypes.put(node.getType().getStartPosition(), c);
				globaltypes.put("("+node.toString()+")", c);
			}
		}
		return true;
	}

	public boolean visit(Assignment node)
	{
		String lhs,rhs;
		lhs=node.getLeftHandSide().toString();
		rhs=node.getRightHandSide().toString();
		if(globalmethods.containsKey(rhs))
		{
			if(!globaltypes.containsKey(lhs))
			{	
				globalmethods.putAll(lhs, globalmethods.get(rhs));
				globalmethods.putAll(lhs, globalmethods.get(rhs));
			}
			else
			{	
				int flag=0;
				Set<ClassElement> temp=new HashSet<ClassElement>();
				for(ClassElement ce:globaltypes.get(lhs))
				{
					if(ce.getId().equals(globalmethods.get(rhs)))
					{
						flag=1;
						temp.add(ce);
					}
				}
				if(flag==1)
				{
					System.out.println("^^^^^^^^^:"+temp+node.getStartPosition());
					globaltypes.replaceValues(lhs,temp);
				}
				
			}
		}
		
		return true;
	}

	public void printJson()
	{
		checkForNull();

		//Add to primitive and uncomment to remove unwanted elements
		//String[] primitive = {"int","float","char","long","boolean","String","byte[]","String[]","int[]","float[]","char[]","long[]","byte"};
		String[] primitive={};
		JSONObject main_json=new JSONObject();

		for(Integer key:printtypes.keySet())
		{
			int flag=0;
			String cname=null;
			List<String> namelist = new ArrayList<String>();
			for(ClassElement type_name:printtypes.get(key))
			{
				int isprimitive=0;
				for(String primitive_type : primitive)
				{
					if(type_name.getId().equals(primitive_type)==true)
					{
						isprimitive=1;
						break;
					}           							
				}
				if(isprimitive==0)
				{
					namelist.add("\""+type_name+"\"");
					if(flag==0)
					{
						cname=type_name.getExactName();
						flag=1;
					}
				}

			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(printtypes.get(key).size()));
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
			for(MethodElement method_name:printmethods.get(key))
			{
				namelist.add("\""+method_name+"\"");
				mname=method_name.getExactName();
			}
			if(namelist.isEmpty()==false)
			{
				JSONObject json = new JSONObject();
				json.accumulate("line_number",Integer.toString(cu.getLineNumber(key)-cutype));
				json.accumulate("precision", Integer.toString(printmethods.get(key).size()));
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
			System.out.println("{\"api_elements\": [{ \"precision\": \"\",\"name\": \"\",\"line_number\": \"\",\"type\": \"\",\"elements\": \"\"}]}" ); 
		}
		else
		{
			System.out.println(main_json.toString(3));
		}
		//printFields();
	}
	
	public void checkForNull()
	{
		for(Integer key : printtypes.keySet())
			for(ClassElement type_name:printtypes.get(key))
			{
				if(type_name==null)
					printtypes.remove(key, type_name);
			}
		for(Integer key : printmethods.keySet())
			for(MethodElement method_name:printmethods.get(key))
			{
				if(method_name==null)
					printmethods.remove(key, method_name);
			}
	}
	
	
}