import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.TreeSet;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
/*
 * grep "org.springframework.context.ApplicationContext" xml/org.springframework.* | grep "inh"
 * 
 */
class Convert
{
	static int count_notset=0;
	static TreeMap<String,String> classDetailsMap= new TreeMap<String, String>();

	public static void fileIterator() throws IOException
	{
		getclassDetails();
		File folder=new File("/home/s23subra/new_maven_data/split_files/");
		File[] fileList=folder.listFiles();
		int i=0;
		for(File f:fileList)
		{
			i++;
			String fn = f.getName();
			///*
			String fname = "/home/s23subra/new_maven_data/xml2/"+fn.substring(0, fn.length()-4)+".xml";
			File file = new File(fname);
			
			if(file.exists())
			{
				  System.out.println(i);
			}
			else
			{
				if(fn.contains("general_"))
				  convert(f);
			}
			 // */
			convert(f);
		}
		
		
	}

	public static void convert(File ip) throws IOException
	{
		//System.out.println(ip.getName());
		if(ip.getName().equals("org.milyn.edi.txt"))
			return;
		BufferedReader br=new BufferedReader(new FileReader(ip));
		Document root=DocumentHelper.createDocument();
		Element main_root=root.addElement("dependencyGraph");
		Element declarations=main_root.addElement("declarations");
		Element classList=declarations.addElement("classList");
		Element init=classList.addElement("ce");
		init.addAttribute("id", "::UnknownType::");
		init.addAttribute("isExt", "true");
		init.addAttribute("vis", "notset");
		Element classDetails=declarations.addElement("classDetails");
		Element relationships=main_root.addElement("relationships");
		Element inherits=relationships.addElement("inherits");
		Element stacks=main_root.addElement("stacks");
		Element callstacks=stacks.addElement("callStacks");
		callstacks.addAttribute("count", "0");
		callstacks.addAttribute("popCount", "0");
		callstacks.addAttribute("pushCount", "0");
		String line=null;
		TreeSet<String> otherTypes=new TreeSet<String>();
		while((line=br.readLine())!=null)
		{

			if(line.startsWith("method;"))
			{
				getmethod(line,classDetails,classList,inherits,otherTypes);
			}
			
			else if(line.startsWith("class;"))
			{
				getclass(line,classList,inherits);
			}
			else if(line.startsWith("interface;"))
			{
				getinterface(line,classList, inherits);
			}
			else if(line.startsWith("field;"))
			{
				getfield(line, classDetails,classList,inherits, otherTypes);
			}

			else
				System.out.println(line);
		}
		for(String type: otherTypes)
		{
			//System.out.println(type);
			if(classDetailsMap.containsKey(type))
			{
				if(classDetailsMap.get(type).startsWith("class;"))
					getclass(classDetailsMap.get(type), classList, inherits);
				else
					getinterface(classDetailsMap.get(type), classList, inherits);
			}
			else
			{
				String isAbs="false";
				String isInt="false";
				String isExt="false";
				String visi="notset";
				Element ce=classList.addElement("ce");
				ce.addAttribute("id", type);
				ce.addAttribute("vis", visi);
				ce.addAttribute("isAbs", isAbs);
				ce.addAttribute("isInt", isInt);
				ce.addAttribute("isExt", isExt);
			}
		}
		OutputFormat format = OutputFormat.createPrettyPrint();

		//XMLWriter writer = new XMLWriter( System.out, format );
		//writer.write( main_root );


		String fn=ip.getName();
		count_notset++;
		if(count_notset%50==0)
			System.out.println(count_notset);
		XMLWriter output = new XMLWriter(new FileWriter(new File("/home/s23subra/new_maven_data/xml2/"+fn.substring(0, fn.length()-4)+".xml")),format);
		output.write( main_root);
		output.close();
		br.close();
	}

	public static void getclassDetails() throws IOException
	{
		File ip=new File("/home/s23subra/new_maven_data/class_file");
		BufferedReader br=new BufferedReader(new FileReader(ip));
		String line=null;
		while((line=br.readLine())!=null)
		{
			String[] temp=line.split(";");
			String id=temp[1];
			classDetailsMap.put(id, line);
		}
		br.close();
	}


	@SuppressWarnings("unchecked")
	public static void getclass(String line, Element classList, Element inherits)
	{
		int flag=0;
		String[] temp=line.split(";");
		String id=temp[1];
		String isAbs="false";
		String isInt="false";
		String isExt="false";
		String vis="notset";
		if(temp[2].contains("abstract ")==true)
		{	
			isAbs="true";
		}
		String[] temp_next=temp[2].split(" ");
		vis=getVisibility(temp_next[0]);
		List<Element>blah=classList.elements("ce");
		ListIterator<Element>iter=blah.listIterator(blah.size());
		while(iter.hasPrevious())
		{
			Element ele=iter.previous();
			if(ele.attributeValue("id").equals(id))
				flag=1;
		}
		if(flag==0)
		{
			Element ce=classList.addElement("ce");
			ce.addAttribute("id", id);
			ce.addAttribute("vis", vis);
			ce.addAttribute("isAbs", isAbs);
			ce.addAttribute("isInt", isInt);
			ce.addAttribute("isExt", isExt);
		}
		String[] superclasses=null;
		int extends_flag=0;
		for(int i=0;i<temp_next.length;i++)
		{
			String word=temp_next[i];
			if(word.trim().equals("extends"))
			{
				superclasses=temp_next[i+1].split(",");
				extends_flag=1;
				break;
			}
		}
		if(extends_flag==1)
		for(String superclass : superclasses)
		{
			if(superclass!=null)
			{
				int flag2=0;
				if(flag2==0)
				{
					Element inh=inherits.addElement("inh");
					inh.addAttribute("p", superclass);
					inh.addAttribute("c", id);
					if(classDetailsMap.containsKey(superclass))
					{
							int flag1=0;
							String[] temp1=classDetailsMap.get(superclass).split(";");
							String id1=superclass;
							String isAbs1="false";
							String isInt1="false";
							String isExt1="false";
							String vis1="notset";
							if(temp1[2].contains("abstract ")==true)
							{	
								isAbs1="true";
							}
							String[] temp_next1=temp1[2].split(" ");
							vis1=getVisibility(temp_next1[0]);
							List<Element>blah1=classList.elements("ce");
							ListIterator<Element>iter1=blah1.listIterator(blah1.size());
							while(iter1.hasPrevious())
							{
								Element ele=iter1.previous();
								if(ele.attributeValue("id").equals(id1))
									flag1=1;
							}
							if(flag1==0)
							{
								Element ce=classList.addElement("ce");
								ce.addAttribute("id", id1);
								ce.addAttribute("vis", vis1);
								ce.addAttribute("isAbs", isAbs1);
								if(classDetailsMap.get(superclass).startsWith("class;"))
									ce.addAttribute("isInt", isInt1);
								else
									ce.addAttribute("isInt", "");
								ce.addAttribute("isExt", isExt1);
							}
							
								
					}
					else
					{
						Element ce=classList.addElement("ce");
						ce.addAttribute("id", superclass);
						ce.addAttribute("vis", "notset");
						ce.addAttribute("isAbs", "false");
						ce.addAttribute("isInt", "false");
						ce.addAttribute("isExt", "false");
					}
			}
			}
		}

		String [] implemented_classes=null;
		int implements_flag=0;
		for(int i=0;i<temp_next.length;i++)
		{
			String word=temp_next[i];
			if(word.trim().equals("implements"))
			{
				implemented_classes=temp_next[i+1].split(",");
				//System.out.println("---"+temp_next[i+1]);
				implements_flag=1;
				break;
			}
		}
		if(implements_flag==1)
		{
			for(String tempstring:implemented_classes)
			{
				Element inh=inherits.addElement("inh");
				inh.addAttribute("p", tempstring);
				inh.addAttribute("c", id);
				
				if(classDetailsMap.containsKey(tempstring))
				{
						int flag1=0;
						String[] temp1=classDetailsMap.get(tempstring).split(";");
						String id1=tempstring;
						String isAbs1="false";
						String isInt1="false";
						String isExt1="false";
						String vis1="notset";
						if(temp1[2].contains("abstract ")==true)
						{	
							isAbs1="true";
						}
						String[] temp_next1=temp1[2].split(" ");
						vis1=getVisibility(temp_next1[0]);
						List<Element>blah1=classList.elements("ce");
						ListIterator<Element>iter1=blah1.listIterator(blah1.size());
						while(iter1.hasPrevious())
						{
							Element ele=iter1.previous();
							if(ele.attributeValue("id").equals(id1))
								flag1=1;
						}
						if(flag1==0)
						{
							Element ce=classList.addElement("ce");
							ce.addAttribute("id", id1);
							ce.addAttribute("vis", vis1);
							ce.addAttribute("isAbs", isAbs1);
							if(classDetailsMap.get(tempstring).startsWith("class;"))
								ce.addAttribute("isInt", isInt1);
							else
								ce.addAttribute("isInt", "");
							ce.addAttribute("isExt", isExt1);
						}
						
							
				}
				else
				{
					Element ce=classList.addElement("ce");
					ce.addAttribute("id", tempstring);
					ce.addAttribute("vis", "notset");
					ce.addAttribute("isAbs", "false");
					ce.addAttribute("isInt", "false");
					ce.addAttribute("isExt", "false");
				}
			}
		}
	}

	public static void getmethod(String line, Element classDetails, Element classList, Element inherits, TreeSet<String> otherTypes)
	{


		String[] temp=line.split(";");
		String return_type="void";
		String shortname=temp[1];
		String cname=temp[2];
		if(cname.charAt(temp[2].length()-1)=='{')
		{
			cname=cname.substring(0, cname.length()-1);
		}
		String id=cname+'.';
		String[] params=null;
		String vis="notset";
		int constructor_flag=0;
		if(shortname.startsWith("access$"))
		{
			return;
		}
		//if(Character.isUpperCase(shortname.charAt(0)) && temp[2].endsWith("."+shortname))
		if(temp[2].endsWith("."+shortname))
		{
			id=id+"<init>";
			return_type="void";
			constructor_flag=1;
		}
		else
		{
			id=id+shortname;
		}
		String[] temp_next=temp[3].split(" ");
		vis=getVisibility(temp_next[0]);
		String temp1 = null;
		if(constructor_flag==0)
		{
			for(String ret: temp_next)
			{
				if(ret.contains("(")==true)
				{
					if(temp1!=null)
						return_type=temp1;
					break;
				}
				temp1=ret;
			}
		}
		int i1=line.indexOf("(");
		int i2=line.lastIndexOf(")");
		id=id+line.substring(i1, i2+1);
		params=line.substring(i1+1, i2).split(",");
		Element current=null;
		List<Element> blah = classDetails.elements("ce");
		ListIterator<Element> iter=blah.listIterator(blah.size());
		while(iter.hasPrevious())
		{
			Element ele=iter.previous();
			if(ele.attributeValue("id").equals(cname))
			{
				current=ele;
				break;
			}
		}

		if(current==null)
		{
			current=classDetails. addElement("ce");
			current.addAttribute("id", cname);
		}
		if(otherTypes.contains(cname)==false)
			otherTypes.add(cname);
		Element me=current.addElement("me");
		me.addAttribute("id", id);
		me.addAttribute("vis", vis);
		Element return_node=me.addElement("return");
		if(isPrimitive(return_type)==false)
			if(otherTypes.contains(return_type)==false)
				otherTypes.add(return_type);
		return_node.addAttribute("id", return_type);

		if(i1+1!=i2)
		{
			Element params_node=me.addElement("params");
			int i=0;
			for(String param:params)
			{
				Element param_node=params_node.addElement("param");
				param_node.addAttribute("index", String.valueOf(i));
				param_node.addAttribute("type", param.trim());
				if(isPrimitive(param.trim())==false)
					if(otherTypes.contains(param.trim())==false)
						otherTypes.add(param.trim());
				i++;
			}
		}
	}

	public static boolean isPrimitive(String trim) {

		if(trim.equals("int")==true || trim.equals("float")==true || trim.equals("char")==true || trim.equals("String")==true || trim.equals("boolean")==true || trim.equals("void")==true || trim.equals("int[]")==true || trim.equals("char[]")==true || trim.equals("String[]")==true || trim.equals("float[]")==true || trim.equals("boolean[]")==true || trim.equals("long")==true || trim.equals("long[]")==true || trim.equals("double")==true || trim.equals("double[]")==true || trim.equals("byte")==true || trim.equals("byte[]")==true)
			return true;
		else
			return false;
	}

	@SuppressWarnings("unchecked")
	public static void getfield(String line, Element classDetails, Element classList, Element inherits, TreeSet<String> otherTypes)
	{
		String[] temp=line.split(";");
		String shortname=temp[1];
		String cname=temp[2];
		if(cname.charAt(temp[2].length()-1)=='{')
			cname=cname.substring(0, cname.length()-1);
		String id=cname+'.'+shortname;
		
		String type="::UnknownType::";
		String isExt="false";
		String vis="notset";

		if(shortname.contains("this$") || shortname.equals("{}"))
		{
			return;
		}
		String[] temp_next=temp[3].split(" ");
		vis=getVisibility(temp_next[0]);

		String temp1=null;
		for(String ret: temp_next)
		{
			if(ret.trim().equals(shortname)==true)
			{
				if(temp1!=null)
					type=temp1;
				break;
			}
			temp1=ret;
		}

		Element current=null;
		List<Element> blah = classDetails.elements("ce");
		ListIterator<Element> iter=blah.listIterator(blah.size());
		while(iter.hasPrevious())
		{
			Element ele=iter.previous();
			if(ele.attributeValue("id").equals(cname))
			{
				current=ele;
				break;
			}
		}

		if(current==null)
		{
			current=classDetails. addElement("ce");
			current.addAttribute("id", cname);
		}

		Element fe=current.addElement("fe");
		fe.addAttribute("id", id);
		fe.addAttribute("vis", vis);
		fe.addAttribute("isExt", isExt);
		fe.addAttribute("type", type);
		otherTypes.add(type);
		otherTypes.add(cname);
	}


	@SuppressWarnings("unchecked")
	public static void getinterface(String line, Element classList, Element inherits)
	{
		int flag=0;
		//add to classlist
		String[] temp=line.split(";");
		String id=null;
		String isAbs="false";
		String isInt="true";
		String isExt="false";
		String vis="notset";
		if(temp[2].contains("abstract ")==true)
		{	
			isAbs="true";
		}
		String[] temp_next=temp[2].split(" ");

		vis=getVisibility(temp_next[0]);

		List<Element>blah=classList.elements("ce");
		ListIterator<Element>iter=blah.listIterator(blah.size());

		for(int i=0;i<temp_next.length;i++)
		{
			if(temp_next[i].trim().equals("interface"))
			{
				id=temp_next[i+1];
				break;
			}
		}

		while(iter.hasPrevious())
		{
			Element ele=iter.previous();
			if(ele.attribute("id").equals(id))
				flag=1;
		}
		if(flag==0)
		{
			Element ce=classList.addElement("ce");
			ce.addAttribute("id", id);
			ce.addAttribute("vis", vis);
			ce.addAttribute("isAbs", isAbs);
			ce.addAttribute("isInt", isInt);
			ce.addAttribute("isExt", isExt);
		}
		String[] superclasses=null;
		int extends_flag=0;
		for(int i=0;i<temp_next.length;i++)
		{
			if(temp_next[i].trim().equals("extends"))
			{
				superclasses=temp_next[i+1].split(",");
				extends_flag=1;
				break;
			}
		}
		if(extends_flag==1)
		for(String superclass:superclasses)
		{
			if(superclass!=null)
			{
				Element inh=inherits.addElement("inh");
				inh.addAttribute("p", superclass);
				inh.addAttribute("c", id);
				
				if(classDetailsMap.containsKey(superclass))
				{
						int flag1=0;
						String[] temp1=classDetailsMap.get(superclass).split(";");
						String id1=superclass;
						String isAbs1="false";
						String isInt1="false";
						String isExt1="false";
						String vis1="notset";
						if(temp1[2].contains("abstract ")==true)
						{	
							isAbs1="true";
						}
						String[] temp_next1=temp1[2].split(" ");
						vis1=getVisibility(temp_next1[0]);
						List<Element>blah1=classList.elements("ce");
						ListIterator<Element>iter1=blah1.listIterator(blah1.size());
						while(iter1.hasPrevious())
						{
							Element ele=iter1.previous();
							if(ele.attributeValue("id").equals(id1))
								flag1=1;
						}
						if(flag1==0)
						{
							Element ce=classList.addElement("ce");
							ce.addAttribute("id", id1);
							ce.addAttribute("vis", vis1);
							ce.addAttribute("isAbs", isAbs1);
							if(classDetailsMap.get(superclass).startsWith("class;"))
								ce.addAttribute("isInt", isInt1);
							else
								ce.addAttribute("isInt", "");
							ce.addAttribute("isExt", isExt1);
						}
						
							
				}
				else
				{
					Element ce=classList.addElement("ce");
					ce.addAttribute("id", superclass);
					ce.addAttribute("vis", "notset");
					ce.addAttribute("isAbs", "false");
					ce.addAttribute("isInt", "false");
					ce.addAttribute("isExt", "false");
				}
			}
		}
		String [] implemented_classes=null;
		int implements_flag=0;
		for(int i=0;i<temp_next.length;i++)
		{
			String word=temp_next[i];
			if(word.trim().equals("implements"))
			{
				implemented_classes=temp_next[i+1].split(",");
				//System.out.println("---"+temp_next[i+1]);
				implements_flag=1;
				break;
			}
		}
		if(implements_flag==1)
		{
			for(String tempstring:implemented_classes)
			{
				Element inh=inherits.addElement("inh");
				inh.addAttribute("p", tempstring);
				inh.addAttribute("c", id);
				
				if(classDetailsMap.containsKey(tempstring))
				{
						int flag1=0;
						String[] temp1=classDetailsMap.get(tempstring).split(";");
						String id1=tempstring;
						String isAbs1="false";
						String isInt1="false";
						String isExt1="false";
						String vis1="notset";
						if(temp1[2].contains("abstract ")==true)
						{	
							isAbs1="true";
						}
						String[] temp_next1=temp1[2].split(" ");
						vis1=getVisibility(temp_next1[0]);
						List<Element>blah1=classList.elements("ce");
						ListIterator<Element>iter1=blah1.listIterator(blah1.size());
						while(iter1.hasPrevious())
						{
							Element ele=iter1.previous();
							if(ele.attributeValue("id").equals(id1))
								flag1=1;
						}
						if(flag1==0)
						{
							Element ce=classList.addElement("ce");
							ce.addAttribute("id", id1);
							ce.addAttribute("vis", vis1);
							ce.addAttribute("isAbs", isAbs1);
							if(classDetailsMap.get(tempstring).startsWith("class;"))
								ce.addAttribute("isInt", isInt1);
							else
								ce.addAttribute("isInt", "");
							ce.addAttribute("isExt", isExt1);
						}
						
							
				}
				else
				{
					Element ce=classList.addElement("ce");
					ce.addAttribute("id", tempstring);
					ce.addAttribute("vis", "notset");
					ce.addAttribute("isAbs", "false");
					ce.addAttribute("isInt", "false");
					ce.addAttribute("isExt", "false");
				}
			}
		}
	}

	private static String getVisibility(String string) 
	{
		string=string.trim();
		if(string.equals("public")==true || string.equals("private")==true || string.equals("protected")==true)
		{
			return string;
		}
		else
			return "notset";

	}

	public static void main(String[] args) throws IOException
	{
		fileIterator();
	}
}